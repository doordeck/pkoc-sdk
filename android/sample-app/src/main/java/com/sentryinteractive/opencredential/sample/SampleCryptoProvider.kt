package com.sentryinteractive.opencredential.sample

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import com.sentryinteractive.opencredential.sdk.AttestedSigner
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK
import com.sentryinteractive.opencredential.sdk.Signer
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/**
 * Sample app's custom [OpenCredentialSDK.CryptoProvider] implementation.
 *
 * Shape-wise similar to the SDK's built-in `OCKeyStore` — also uses AndroidKeyStore — but lives
 * in the sample app's package and uses **separate** alias and SharedPreferences namespaces so
 * the two modes don't share credentials. Its purpose is to exercise the consumer-implementer
 * code path: if the SDK ever broke the override contract, this provider would surface the
 * regression.
 *
 * Real consumers writing their own provider would only need this shape if they want to keep
 * credential keys outside AndroidKeyStore (HSM, AWS KMS, hardware token, etc.).
 */
class SampleCryptoProvider(context: Context) : OpenCredentialSDK.CryptoProvider {

    companion object {
        private const val TAG = "SampleCryptoProvider"
        private const val PREFS_NAME = "oc_sample_provider"
        private const val KEY_ALIASES = "aliases"
        private const val ALIAS_PREFIX = "oc_sample_credential_"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun listSigners(): List<Signer> {
        val aliases = prefs.getStringSet(KEY_ALIASES, emptySet()) ?: emptySet()
        return aliases.map { SampleSigner(it) }
    }

    override fun createSigner(attestationChallenge: ByteArray?): AttestedSigner? {
        val alias = "$ALIAS_PREFIX${UUID.randomUUID()}"

        return try {
            val ks = androidKeyStore()

            // Try with attestation if a challenge was supplied. Fall back to non-attested key
            // generation if hardware attestation isn't available on this device — the SDK will
            // register the credential as attested=false in that case.
            var attested = attestationChallenge != null
            try {
                val tier = generateKeyPair(alias, attestationChallenge)
                Log.i(TAG, "Sample provider generated key $alias ($tier, attested=$attested)")
            } catch (e: Exception) {
                if (attestationChallenge == null) throw e
                Log.w(TAG, "Hardware attestation unavailable on this device — falling back to non-attested key", e)
                try { ks.deleteEntry(alias) } catch (_: Exception) {}
                val tier = generateKeyPair(alias, null)
                attested = false
                Log.i(TAG, "Sample provider generated key $alias ($tier, attested=false) [fallback]")
            }

            // NB: alias is not persisted here. Caller must call confirm() after successful
            // server-side registration, or forget() to roll back. Same two-phase pattern as
            // OCKeyStore — keeps orphaned local keys from accumulating on failed registrations.

            val attestationDocument = if (attested) {
                val chain = ks.getCertificateChain(alias)
                ByteArrayOutputStream().use { baos ->
                    for (c in chain) baos.write(c.encoded)
                    baos.toByteArray()
                }
            } else {
                null
            }

            AttestedSigner(
                signer = SampleSigner(alias),
                attestationDocument = attestationDocument
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create credential key", e)
            try { androidKeyStore().deleteEntry(alias) } catch (_: Exception) {}
            null
        }
    }

    @Throws(Exception::class)
    private fun generateKeyPair(alias: String, attestationChallenge: ByteArray?): String {
        // codeql[java/potentially-weak-cryptographic-algorithm] -- secp256r1 (NIST P-256)
        // CodeQL flags `KEY_ALGORITHM_EC` because the JCA "EC" algorithm name alone does not
        // pin a specific curve and could in theory pick a weak one. We pin it explicitly via
        // `setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))` two lines below — that
        // is the NIST P-256 curve, ~128-bit security level, the standard for TLS, JWT (ES256),
        // WebAuthn / FIDO2, Apple App Attest, and Android Key Attestation. It is also the only
        // curve the OpenCredential proto currently supports (`CREDENTIAL_TYPE_P256`). The
        // alert is a false positive against modern asymmetric crypto and is dismissed.
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val specBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)

        if (attestationChallenge != null) {
            specBuilder.setAttestationChallenge(attestationChallenge)
        }

        return try {
            specBuilder.setIsStrongBoxBacked(true)
            kpg.initialize(specBuilder.build())
            kpg.generateKeyPair()
            "StrongBox"
        } catch (e: StrongBoxUnavailableException) {
            specBuilder.setIsStrongBoxBacked(false)
            kpg.initialize(specBuilder.build())
            kpg.generateKeyPair()
            "TEE"
        }
    }

    override fun confirm(signer: Signer): Boolean {
        val alias = (signer as? SampleSigner)?.alias ?: return false
        return try {
            val current = prefs.getStringSet(KEY_ALIASES, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.add(alias)
            prefs.edit().putStringSet(KEY_ALIASES, current).commit()
            Log.i(TAG, "Confirmed signer $alias")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to confirm signer $alias", e)
            false
        }
    }

    override fun forget(signer: Signer): Boolean {
        val alias = (signer as? SampleSigner)?.alias ?: return false
        return try {
            try { androidKeyStore().deleteEntry(alias) } catch (_: Exception) {}
            val current = prefs.getStringSet(KEY_ALIASES, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.remove(alias)
            prefs.edit().putStringSet(KEY_ALIASES, current).commit()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to forget signer $alias", e)
            false
        }
    }

    private fun androidKeyStore(): KeyStore {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        return ks
    }
}

/**
 * [Signer] implementation backed by an AndroidKeyStore alias, for the sample app's custom
 * provider. Internal to [SampleCryptoProvider].
 */
private class SampleSigner(internal val alias: String) : Signer {

    override val publicKeyDer: ByteArray?
        get() = try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.getCertificate(alias)?.publicKey?.encoded
        } catch (e: Exception) {
            null
        }

    override fun sign(data: ByteArray): ByteArray? {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = ks.getKey(alias, null) as? PrivateKey ?: return null
            val s = Signature.getInstance("SHA256withECDSA")
            s.initSign(privateKey)
            s.update(data)
            s.sign()
        } catch (e: Exception) {
            null
        }
    }
}
