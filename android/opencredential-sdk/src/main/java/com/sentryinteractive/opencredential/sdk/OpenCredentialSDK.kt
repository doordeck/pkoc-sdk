package com.sentryinteractive.opencredential.sdk

import android.app.Activity
import android.content.Intent
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK.initialize
import com.sentryinteractive.opencredential.sdk.grpc.CredentialService
import com.sentryinteractive.opencredential.sdk.ui.ConsentActivity
import com.sentryinteractive.opencredential.sdk.ui.CredentialSelectionActivity
import com.sentryinteractive.opencredential.sdk.ui.LoginActivity
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
        intent.putExtra(CredentialSelectionActivity.EXTRA_ORGANIZATION_ID, organizationId)
        intent.putExtra(CredentialSelectionActivity.EXTRA_ORGANIZATION_NAME, organizationName)
        intent.putExtra(CredentialSelectionActivity.EXTRA_INVITE_CODE, inviteCode)
        activity.startActivity(intent)
    }
}
