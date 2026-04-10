package com.sentryinteractive.opencredential.sdk.crypto

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
 * Default [OpenCredentialSDK.CryptoProvider] implementation, backed by AndroidKeyStore.
 *
 * - Each credential gets its own AndroidKeyStore alias `oc_credential_<uuid>`.
 * - The alias index is persisted in [android.content.SharedPreferences] (`oc_keystore` / `aliases`).
 * - Key generation prefers StrongBox; falls back to TEE if StrongBox is unavailable.
 * - When [createSigner] is called with a non-null challenge, the new key is bound to it via
 *   [KeyGenParameterSpec.Builder.setAttestationChallenge] and the returned
 *   [AttestedSigner.attestationDocument] contains the concatenated DER cert chain.
 *
 * Thread-safety: AndroidKeyStore + SharedPreferences are thread-safe individually, but the
 * compound "create alias + add to index" is not atomic. Concurrent registrations are not
 * expected in normal use.
 */
class OCKeyStore(context: Context) : OpenCredentialSDK.CryptoProvider {

    companion object {
        private const val TAG = "OCKeyStore"
        private const val PREFS_NAME = "oc_keystore"
        private const val KEY_ALIASES = "aliases"
        private const val ALIAS_PREFIX = "oc_credential_"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun listSigners(): List<Signer> {
        val aliases = prefs.getStringSet(KEY_ALIASES, emptySet()) ?: emptySet()
        return aliases.map { OCKeyStoreSigner(it) }
    }

    override fun createSigner(attestationChallenge: ByteArray?): AttestedSigner? {
        val alias = "$ALIAS_PREFIX${UUID.randomUUID()}"

        return try {
            val ks = androidKeyStore()

            // First attempt: with hardware attestation if a challenge was supplied. If the
            // device doesn't support key attestation (very old Android, some emulators, etc.),
            // fall back to a non-attested key under the same alias and let the SDK register
            // the credential as attested=false on the server.
            var attested = attestationChallenge != null
            try {
                val tier = generateKeyPair(alias, attestationChallenge)
                Log.i(TAG, "Generated credential key $alias ($tier, attested=$attested)")
            } catch (e: Exception) {
                if (attestationChallenge == null) throw e
                Log.w(TAG, "Hardware attestation unavailable on this device — falling back to non-attested key", e)
                try { ks.deleteEntry(alias) } catch (_: Exception) { /* NO-OP */ }
                val tier = generateKeyPair(alias, null)
                attested = false
                Log.i(TAG, "Generated credential key $alias ($tier, attested=false) [fallback]")
            }

            // NB: the new alias is *not* persisted to the index here. The caller must call
            // [confirm] after the credential has been successfully registered server-side, or
            // [forget] if registration fails. This prevents orphaned local keys from
            // accumulating when registration is abandoned mid-flow.

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
                signer = OCKeyStoreSigner(alias),
                attestationDocument = attestationDocument
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create credential key", e)
            // Best-effort cleanup of any partial alias state
            try {
                androidKeyStore().deleteEntry(alias)
            } catch (_: Exception) {
                // NO-OP
            }
            null
        }
    }

    /**
     * Build the [KeyGenParameterSpec] and generate a key pair under [alias], optionally bound
     * to [attestationChallenge]. Prefers StrongBox; falls back to TEE if StrongBox is
     * unavailable. Returns the tier name (`"StrongBox"` or `"TEE"`).
     */
    @Throws(Exception::class)
    private fun generateKeyPair(alias: String, attestationChallenge: ByteArray?): String {
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
        val alias = (signer as? OCKeyStoreSigner)?.alias ?: return false
        return try {
            val current = prefs.getStringSet(KEY_ALIASES, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.add(alias)
            prefs.edit().putStringSet(KEY_ALIASES, current).apply()
            Log.i(TAG, "Confirmed signer $alias")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to confirm signer $alias", e)
            false
        }
    }

    override fun forget(signer: Signer): Boolean {
        val alias = (signer as? OCKeyStoreSigner)?.alias ?: return false
        return try {
            try {
                androidKeyStore().deleteEntry(alias)
            } catch (_: Exception) {
                // Continue with the index cleanup even if the keystore entry was already gone.
            }
            val current = prefs.getStringSet(KEY_ALIASES, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.remove(alias)
            prefs.edit().putStringSet(KEY_ALIASES, current).apply()
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
 * [Signer] implementation backed by an AndroidKeyStore alias.
 *
 * Internal to [OCKeyStore]; consumers of the SDK only ever see the [Signer] interface.
 */
private class OCKeyStoreSigner(internal val alias: String) : Signer {

    override val publicKeyDer: ByteArray
        get() = try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.getCertificate(alias)?.publicKey?.encoded ?: ByteArray(0)
        } catch (e: Exception) {
            ByteArray(0)
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
