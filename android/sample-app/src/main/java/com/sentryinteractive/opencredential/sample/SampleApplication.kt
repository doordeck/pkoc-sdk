package com.sentryinteractive.opencredential.sample

import android.app.Application
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Sample Application that initializes the OpenCredential SDK with a P-256 key pair
 * from the Android KeyStore.
 */
class SampleApplication : Application() {

    companion object {
        private const val TAG = "SampleApp"
        private const val KEY_ALIAS = "oc_sample_device_key"
    }

    override fun onCreate() {
        super.onCreate()
        initializeSDK()
    }

    private fun initializeSDK() {
        try {
            ensureKeyPairExists()

            OpenCredentialSDK.initialize(object : OpenCredentialSDK.CryptoProvider {
                override fun getPublicKeyDer(): ByteArray? {
                    return try {
                        val ks = KeyStore.getInstance("AndroidKeyStore")
                        ks.load(null)
                        ks.getCertificate(KEY_ALIAS).publicKey.encoded
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get public key", e)
                        null
                    }
                }

                override fun sign(data: ByteArray): ByteArray? {
                    return try {
                        val ks = KeyStore.getInstance("AndroidKeyStore")
                        ks.load(null)
                        val privateKey = ks.getKey(KEY_ALIAS, null) as PrivateKey

                        val s = Signature.getInstance("SHA256withECDSA")
                        s.initSign(privateKey)
                        s.update(data)
                        s.sign()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sign data", e)
                        null
                    }
                }
            })

            Log.i(TAG, "OpenCredential SDK initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SDK", e)
        }
    }

    @Throws(Exception::class)
    private fun ensureKeyPairExists() {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)

        if (ks.containsAlias(KEY_ALIAS)) {
            return
        }

        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
        )

        kpg.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )

        kpg.generateKeyPair()
        Log.i(TAG, "Generated new P-256 key pair")
    }
}
