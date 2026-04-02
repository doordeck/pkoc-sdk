package com.sentryinteractive.opencredential.sdk

import android.app.Activity
import android.content.Intent
import android.util.Base64
import com.sentryinteractive.opencredential.api.credential.CredentialFilter
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK.initialize
import com.sentryinteractive.opencredential.sdk.grpc.CredentialService
import com.sentryinteractive.opencredential.sdk.ui.ConsentActivity
import com.sentryinteractive.opencredential.sdk.ui.CredentialSelectionActivity
import com.sentryinteractive.opencredential.sdk.ui.LoginActivity
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * Main entry point for the OpenCredential SDK.
 * Initialize before use with [initialize].
 */
object OpenCredentialSDK {

    interface CryptoProvider {
        /** Return the DER-encoded public key, or null if unavailable. */
        fun getPublicKeyDer(): ByteArray?

        /** Sign the given data with the device's private key */
        fun sign(data: ByteArray): ByteArray?
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

    @JvmStatic
    fun initialize(provider: CryptoProvider) {
        cryptoProviderField = provider
        verifyOnServer()
    }

    // Fire and forget — updates the server's last-seen timestamp for this credential
    private fun verifyOnServer() {
        thread {
            try {
                CredentialService().verifyCredential()
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
     * Returns the list of identity strings (emails/phones) associated with this device's key.
     * This is a blocking network call — run it off the main thread.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun getIdentities(): List<String> {
        val response = CredentialService().getCredentials(CredentialFilter.CREDENTIAL_FILTER_SAME_KEY)
        return response.credentialsList.map { cred ->
            val identity = cred.identity
            when {
                identity.hasEmail() -> identity.email
                identity.hasPhone() -> identity.phone
                else -> ""
            }
        }.filter { it.isNotEmpty() }.distinct()
    }

    /**
     * Deletes credentials belonging to the authenticated user. Both fields act as optional AND filters over the full
     * set of credentials reachable from the current authentication context:
     *
     * - (none)            - delete every credential across all identities (GDPR full erasure)
     * - identity          - delete all keys for a single identity (e.g. remove an email address)
     * - key_thumbprint    - delete a specific key across all identities (e.g. lost device)
     * - both              - delete exactly one credential/identity combination
     *
     * Any approved organization shares are automatically revoked before deletion.
     * This is a blocking network call — run it off the main thread.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun deleteCredentials(email: String? = null, keyThumbprint: String? = null) {
        CredentialService().deleteCredentials(email, keyThumbprint)
    }

    /**
     * Returns the base64url-encoded SHA-256 thumbprint of this device's public key,
     * or null if no crypto provider is set.
     */
    @JvmStatic
    fun getKeyThumbprint(): String? {
        val der = cryptoProviderField?.getPublicKeyDer() ?: return null
        val hash = MessageDigest.getInstance("SHA-256").digest(der)
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
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
