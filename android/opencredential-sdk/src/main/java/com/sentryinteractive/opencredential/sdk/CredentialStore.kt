package com.sentryinteractive.opencredential.sdk

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple credential token store backed by SharedPreferences.
 * Stores the verification token obtained after successful email verification.
 */
class CredentialStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "oc_credential_store"
        private const val KEY_VERIFICATION_TOKEN = "verification_token"
        private const val KEY_EMAIL = "email"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveVerificationToken(token: String) {
        prefs.edit().putString(KEY_VERIFICATION_TOKEN, token).apply()
    }

    fun getVerificationToken(): String? {
        return prefs.getString(KEY_VERIFICATION_TOKEN, null)
    }

    fun saveEmail(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    fun getEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasVerificationToken(): Boolean {
        return getVerificationToken() != null
    }
}
