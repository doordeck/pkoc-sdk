package com.sentryinteractive.opencredential.sdk.grpc

import android.util.Base64
import android.util.Log
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.security.MessageDigest

class GrpcWebInterceptor : Interceptor {

    companion object {
        private const val TAG = "GrpcWebInterceptor"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val original: Request = chain.request()
        try {
            val provider = OpenCredentialSDK.getCryptoProvider() ?: return chain.proceed(original)
            val publicKeyDer = provider.getPublicKeyDer() ?: return chain.proceed(original)

            val bodyBytes = bodyToBytes(original.body)

            val bodySha256 = MessageDigest.getInstance("SHA-256").digest(bodyBytes)
            val contentDigest = "sha-256=:${base64(bodySha256)}:"

            val thumbprintBytes = MessageDigest.getInstance("SHA-256").digest(publicKeyDer)
            val keyId = base64Url(thumbprintBytes)

            val created = System.currentTimeMillis() / 1000
            val path = original.url.encodedPath
            val authority = original.url.host

            val sigInputValue = "(\"@method\" \"@path\" \"@authority\" \"content-digest\")" +
                    ";alg=\"ecdsa-p256-sha256\"" +
                    ";keyid=\"$keyId\"" +
                    ";created=$created"

            val sigBase = "\"@method\": POST\n" +
                    "\"@path\": $path\n" +
                    "\"@authority\": $authority\n" +
                    "\"content-digest\": $contentDigest\n" +
                    "\"@signature-params\": $sigInputValue"

            val signature = provider.sign(sigBase.toByteArray()) ?: return chain.proceed(original)

            val signed = original.newBuilder()
                .header("content-digest", contentDigest)
                .header("signature-input", "sig1=$sigInputValue")
                .header("signature", "sig1=:${base64(signature)}:")
                .build()

            return chain.proceed(signed)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sign request, proceeding unsigned", e)
            return chain.proceed(original)
        }
    }

    private fun bodyToBytes(body: RequestBody?): ByteArray {
        if (body == null) return ByteArray(0)
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readByteArray()
    }

    private fun base64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun base64Url(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
