package com.sentryinteractive.opencredential.sample

import android.app.Application
import android.util.Log
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK

/**
 * Sample Application that initializes the OpenCredential SDK.
 *
 * Reads the mode preference (SDK-managed Keystore vs Custom CryptoProvider) from
 * [SampleModePreference] and routes initialization accordingly. The default is the SDK-managed
 * Keystore, which is what most apps should use.
 */
class SampleApplication : Application() {

    companion object {
        private const val TAG = "SampleApp"

        /**
         * Re-initialize the SDK with the current mode preference. Call this after the user
         * changes the mode in Settings to swap providers without restarting the app.
         */
        fun reinitializeSDK(application: Application) {
            initializeForMode(application)
        }

        private fun initializeForMode(application: Application) {
            try {
                when (SampleModePreference.get(application)) {
                    SampleModePreference.Mode.SDK_KEYSTORE -> {
                        OpenCredentialSDK.initialize(application)
                        Log.i(TAG, "Initialized SDK with default OCKeyStore")
                    }
                    SampleModePreference.Mode.CUSTOM_PROVIDER -> {
                        OpenCredentialSDK.initialize(SampleCryptoProvider(application))
                        Log.i(TAG, "Initialized SDK with SampleCryptoProvider")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SDK", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializeForMode(this)
    }
}
