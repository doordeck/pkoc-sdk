package com.sentryinteractive.opencredential.sample

import android.content.Context

/**
 * Persists which CryptoProvider mode the sample app is currently using.
 *
 * - [Mode.SDK_KEYSTORE] — the SDK manages credential keys via its built-in [com.sentryinteractive.opencredential.sdk.crypto.OCKeyStore]
 * - [Mode.CUSTOM_PROVIDER] — the sample app supplies its own [SampleCryptoProvider]
 *
 * Both modes use AndroidKeyStore under the hood, but with **different alias prefixes** and
 * **different SharedPreferences namespaces** so they don't see each other's keys. Switching
 * modes effectively hides the credentials registered in the other mode (until you switch back).
 */
object SampleModePreference {

    enum class Mode { SDK_KEYSTORE, CUSTOM_PROVIDER }

    private const val PREFS_NAME = "oc_sample_mode"
    private const val KEY_MODE = "mode"

    fun get(context: Context): Mode {
        val name = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, Mode.SDK_KEYSTORE.name)
        return runCatching { Mode.valueOf(name ?: Mode.SDK_KEYSTORE.name) }
            .getOrDefault(Mode.SDK_KEYSTORE)
    }

    fun set(context: Context, mode: Mode) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}
