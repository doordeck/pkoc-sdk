package com.sentryinteractive.opencredential.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.sentryinteractive.opencredential.api.credential.CredentialFilter
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK.initialize
import com.sentryinteractive.opencredential.sdk.crypto.OCKeyStore
import com.sentryinteractive.opencredential.sdk.grpc.CredentialService
import com.sentryinteractive.opencredential.sdk.grpc.GrpcWebException
import com.sentryinteractive.opencredential.sdk.ui.ConsentActivity
import com.sentryinteractive.opencredential.sdk.ui.CredentialSelectionActivity
import com.sentryinteractive.opencredential.sdk.ui.LoginActivity
import kotlin.concurrent.thread

/**
 * A key that can produce DER-encoded ECDSA signatures for a single credential.
 *
 * A [Signer] represents exactly one credential's key: [publicKeyDer] is the public half
 * (used as the credential identifier and in signature headers), and [sign] produces
 * signatures with the private half. Implementations are produced by
 * [OpenCredentialSDK.CryptoProvider.listSigners] and [OpenCredentialSDK.CryptoProvider.createSigner].
 */
interface Signer {
    /** DER-encoded (X.509 SubjectPublicKeyInfo) public key. */
    val publicKeyDer: ByteArray

    /** Sign [data] with this credential's private key; returns a DER-encoded ECDSA signature. */
    fun sign(data: ByteArray): ByteArray?
}

/**
 * A freshly minted credential [Signer], optionally with a hardware-attestation document
 * produced at key-generation time.
 *
 * @property signer The new credential signer.
 * @property attestationDocument Hardware-attestation document binding the server-supplied
 *   challenge to this key, or `null` if attestation was not requested or not supported.
 *   On Android, this is the concatenated DER certificate chain returned by
 *   [java.security.KeyStore.getCertificateChain].
 */
data class AttestedSigner(
    val signer: Signer,
    val attestationDocument: ByteArray? = null
)

/**
 * Display-oriented summary of one server-side credential.
 *
 * @property identity The credential's email or phone identity.
 * @property attested Whether the credential's key was hardware-attested at registration time.
 */
data class OCCredentialInfo(
    val identity: OCIdentity,
    val attested: Boolean
)

/**
 * Main entry point for the OpenCredential SDK.
 *
 * ## Initialization
 *
 * Two ways to initialize the SDK:
 *
 * 1. **Default (recommended):** [initialize] with a `Context`. The SDK will manage credential
 *    keys for you in the AndroidKeyStore (StrongBox-preferred, TEE fallback) and automatically
 *    perform hardware attestation during credential registration. You don't need to think about
 *    keys at all.
 *
 * 2. **Custom override:** [initialize] with your own [CryptoProvider]. Use this if you need to
 *    keep credential keys in an HSM, AWS KMS, a hardware token, or any non-AndroidKeyStore
 *    backend. The SDK will route all signing through your provider. Attestation is only
 *    available if your provider supports it via [CryptoProvider.createCredentialKey] returning
 *    a non-null `attestationDocument`; otherwise credentials will be registered with
 *    `attested=false`.
 *
 * ## Multi-account model
 *
 * Each registered credential is tied to its own key, identified by an opaque handle. The SDK
 * tracks handles via [CryptoProvider.listHandles] and uses the appropriate handle to sign each
 * outbound request. Adding a second email address mints a new key under a new handle and does
 * **not** affect existing credentials on the device.
 */
object OpenCredentialSDK {

    // gRPC status codes that authoritatively mean "this signer's credential is gone or
    // can't be authenticated against the server" — safe to forget the local key in response.
    // Other gRPC errors (INTERNAL, UNAVAILABLE, etc.) and IOExceptions are treated as
    // transient and leave the signer in place.
    private const val GRPC_NOT_FOUND = 5
    private const val GRPC_PERMISSION_DENIED = 7
    private const val GRPC_UNAUTHENTICATED = 16

    private fun isOrphanSignal(e: GrpcWebException): Boolean =
        e.statusCode == GRPC_NOT_FOUND ||
        e.statusCode == GRPC_PERMISSION_DENIED ||
        e.statusCode == GRPC_UNAUTHENTICATED

    /**
     * Custodian of credential keys.
     *
     * Most apps should use the SDK-provided default by calling [OpenCredentialSDK.initialize]
     * with a `Context` instead of implementing this interface. Implement it directly only when
     * you need to use a non-AndroidKeyStore backend (HSM, AWS KMS, hardware token, etc.).
     *
     * Implementations must:
     * - Return a [Signer] for every credential key currently stored
     * - Persist keys across app restarts
     * - Support multiple keys simultaneously (one per registered credential)
     */
    interface CryptoProvider {

        /**
         * Return a [Signer] for every credential key currently managed by this provider.
         *
         * Only signers that have been [confirm]ed are included — signers freshly minted by
         * [createSigner] but not yet confirmed are *not* listed, since their corresponding
         * server-side credential registration may not have completed yet.
         */
        fun listSigners(): List<Signer>

        /**
         * Create a new credential key and return a [Signer] for it.
         *
         * The new signer is **uncommitted** — it can sign requests immediately, but it is
         * **not yet** included in [listSigners]. Call [confirm] after the credential has been
         * successfully registered server-side, or [forget] if registration fails or is
         * abandoned. This two-phase pattern prevents orphaned local keys from accumulating
         * when registration fails.
         *
         * @param attestationChallenge When non-null, the implementation should attempt to bind
         *   this challenge into the new key via hardware attestation (e.g.
         *   [android.security.keystore.KeyGenParameterSpec.Builder.setAttestationChallenge] on
         *   Android). If attestation isn't supported by this provider, the key should still be
         *   created and the returned [AttestedSigner.attestationDocument] should be `null`
         *   (the SDK will register the credential as unattested).
         * @return The new uncommitted [AttestedSigner], or `null` if creation failed entirely.
         */
        fun createSigner(attestationChallenge: ByteArray? = null): AttestedSigner?

        /**
         * Commit a signer previously returned by [createSigner], promoting it from
         * "uncommitted" to "managed". After confirmation, the signer appears in [listSigners]
         * and persists across app restarts. Call this only after the corresponding
         * server-side credential has been successfully registered.
         * Returns `true` on success.
         */
        fun confirm(signer: Signer): Boolean

        /**
         * Forget a credential key — deletes the underlying key material and removes the
         * signer from the managed set (if it had been confirmed). Idempotent: safe to call
         * on uncommitted signers, on already-forgotten signers, and on confirmed signers.
         * Returns `true` if the operation succeeded.
         */
        fun forget(signer: Signer): Boolean
    }

    interface Callback {
        /** Called when the user consents to sharing with an organization. */
        fun onConsentApproved(organizationId: String, organizationName: String, inviteCode: String) {}

        /** Called when login (email verification) succeeds. */
        fun onLoginCompleted() {}

        /** Called when the full flow (credential selection -> approve) completes. */
        fun onCompleted(selectedCredentials: Array<ByteArray>)

        /** Called when the user cancels or an unrecoverable error occurs. */
        fun onCancelled()
    }

    @Volatile
    private var cryptoProviderField: CryptoProvider? = null

    @Volatile
    private var callbackField: Callback? = null

    /**
     * Initialize the SDK with the default AndroidKeyStore-backed credential key store.
     * This is the recommended path for most apps.
     *
     * The SDK will manage credential keys for you (one per registered identity), prefer
     * StrongBox where available, fall back to TEE, and automatically perform hardware
     * attestation when registering new credentials.
     *
     * @param context Any context — only the application context is retained.
     */
    @JvmStatic
    fun initialize(context: Context) {
        initialize(OCKeyStore(context))
    }

    /**
     * Initialize the SDK with a custom [CryptoProvider]. Use this only if you need to keep
     * credential keys in a backend other than AndroidKeyStore (HSM, AWS KMS, hardware token,
     * etc.). Most apps should use [initialize(Context)][initialize] instead.
     */
    @JvmStatic
    fun initialize(provider: CryptoProvider) {
        cryptoProviderField = provider
        verifyOnServer()
    }

    // Fire and forget — pings the server with each known credential to refresh last-seen
    private fun verifyOnServer() {
        thread {
            try {
                val provider = cryptoProviderField ?: return@thread
                for (signer in provider.listSigners()) {
                    try {
                        CredentialService().verifyCredential(signer)
                    } catch (_: Exception) {
                        // NO-OP per-signer
                    }
                }
            } catch (_: Exception) {
                // NO-OP
            }
        }
    }

    /**
     * Check whether the device has any credentials registered on the server.
     * This is a blocking network call — run it off the main thread.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun hasCredentials(): Boolean {
        return getIdentities().isNotEmpty()
    }

    /**
     * Returns the list of identities (emails/phones) registered on this device, aggregated
     * across all credential keys this provider manages. Order is not guaranteed.
     * This is a blocking network call — run it off the main thread.
     */
    /**
     * Returns a display-oriented summary of every credential registered on this device,
     * aggregated across all credential keys this provider manages. Self-prunes signers whose
     * server-side credential is gone (NOT_FOUND / PERMISSION_DENIED / UNAUTHENTICATED).
     * This is a blocking network call — run it off the main thread.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun getCredentialDetails(): List<OCCredentialInfo> {
        val provider = cryptoProviderField ?: return emptyList()
        val service = CredentialService()
        val all = mutableListOf<OCCredentialInfo>()
        for (signer in provider.listSigners().toList()) {
            try {
                val response = service.getCredentials(signer, CredentialFilter.CREDENTIAL_FILTER_SAME_KEY)
                if (response.credentialsCount == 0) {
                    // Server returned an empty list — orphaned local key, forget it.
                    provider.forget(signer)
                    continue
                }
                for (cred in response.credentialsList) {
                    val identity = OCIdentity.fromProto(cred.identity) ?: continue
                    all.add(OCCredentialInfo(identity = identity, attested = cred.attested))
                }
            } catch (e: GrpcWebException) {
                // NOT_FOUND / PERMISSION_DENIED / UNAUTHENTICATED mean this signer's credential
                // is genuinely gone — forget it. Other gRPC errors might be transient.
                if (isOrphanSignal(e)) {
                    provider.forget(signer)
                }
            } catch (_: Exception) {
                // Network or other transient error — leave the signer alone.
            }
        }
        return all
    }

    /**
     * Returns the list of identities (emails/phones) registered on this device. Convenience
     * wrapper around [getCredentialDetails] that drops the attestation flag — use
     * [getCredentialDetails] if you also need to know whether each credential is attested.
     * This is a blocking network call — run it off the main thread.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun getIdentities(): List<OCIdentity> = getCredentialDetails().map { it.identity }

    /**
     * Deletes credentials belonging to the authenticated user. [identity] acts as an optional
     * filter over the full set of credentials reachable from each local credential key:
     *
     * - `null`     - delete every credential across all identities (GDPR full erasure)
     * - non-null   - delete the credential for a single identity
     *
     * After successful server-side deletion, any local credential keys whose server-side
     * credential is gone are also forgotten.
     * Any approved organization shares are automatically revoked before deletion.
     * This is a blocking network call — run it off the main thread.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun deleteCredentials(identity: OCIdentity? = null) {
        val provider = cryptoProviderField ?: return
        val service = CredentialService()
        for (signer in provider.listSigners().toList()) {
            try {
                service.deleteCredentials(signer, identity)
            } catch (e: GrpcWebException) {
                // If the server tells us this signer is unknown, it's already orphaned —
                // skip the post-delete check below and just forget it.
                if (isOrphanSignal(e)) {
                    provider.forget(signer)
                    continue
                }
                // Other gRPC errors: skip this signer, caller can retry.
                continue
            } catch (_: Exception) {
                // Network or other transient error: skip, caller can retry.
                continue
            }

            // Delete RPC succeeded. Drop the local key if its server-side credential is gone:
            // re-query and forget on either an empty result or an orphan signal.
            try {
                val remaining = service.getCredentials(signer, CredentialFilter.CREDENTIAL_FILTER_SAME_KEY).credentialsCount
                if (remaining == 0) {
                    provider.forget(signer)
                }
            } catch (e: GrpcWebException) {
                if (isOrphanSignal(e)) {
                    provider.forget(signer)
                }
            } catch (_: Exception) {
                // transient — leave the signer alone
            }
        }
    }

    @JvmStatic
    fun getCryptoProvider(): CryptoProvider? = cryptoProviderField

    @JvmStatic
    fun setCallback(cb: Callback) {
        callbackField = cb
    }

    @JvmStatic
    fun getCallback(): Callback? = callbackField

    /**
     * Launch the login (email verification + 2FA) screen directly.
     */
    @JvmStatic
    fun launchLogin(activity: Activity) {
        val intent = Intent(activity, LoginActivity::class.java)
        activity.startActivity(intent)
    }

    /**
     * Launch the consent screen with a known invite code.
     */
    @JvmStatic
    fun launchConsent(activity: Activity, inviteCode: String) {
        val intent = Intent(activity, ConsentActivity::class.java)
        intent.putExtra(ConsentActivity.EXTRA_INVITE_CODE, inviteCode)
        activity.startActivity(intent)
    }

    /**
     * Launch the credential selection screen with organization context.
     */
    @JvmStatic
    fun launchCredentialSelection(
        activity: Activity,
        organizationId: String,
        organizationName: String,
        inviteCode: String
    ) {
        val intent = Intent(activity, CredentialSelectionActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra(CredentialSelectionActivity.EXTRA_ORGANIZATION_ID, organizationId)
        intent.putExtra(CredentialSelectionActivity.EXTRA_ORGANIZATION_NAME, organizationName)
        intent.putExtra(CredentialSelectionActivity.EXTRA_INVITE_CODE, inviteCode)
        activity.startActivity(intent)
    }
}
