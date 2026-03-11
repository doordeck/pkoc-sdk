package com.sentryinteractive.opencredential.sdk

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple credential store backed by SharedPreferences.
 * Only persists non-sensitive data (email). Sensitive tokens are kept in-memory only.
 */
class CredentialStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "oc_credential_store"
        private const val KEY_EMAIL = "email"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveEmail(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    fun getEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
