package com.sentryinteractive.opencredential.sdk.grpc

import android.util.Base64
import android.util.Log
import com.google.protobuf.MessageLite
import com.sentryinteractive.opencredential.sdk.Signer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

class GrpcWebClient private constructor() {

    companion object {
        private const val TAG = "GrpcWebClient"
        private const val BASE_URL = "https://api.opencredential.sentryinteractive.com"
        private val GRPC_WEB_MEDIA_TYPE = "application/grpc-web".toMediaType()

        @Volatile
        private var instance: GrpcWebClient? = null

        @JvmStatic
        fun getInstance(): GrpcWebClient {
            return instance ?: synchronized(this) {
                instance ?: GrpcWebClient().also { instance = it }
            }
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder().build()

    /**
     * Make a gRPC-Web call. If [signer] is non-null, the request is signed with that signer's
     * key via RFC 9421 HTTP Message Signatures. If null, the request goes out unsigned
     * (anonymous bootstrap).
     */
    @Throws(IOException::class, GrpcWebException::class)
    fun call(servicePath: String, method: String, request: MessageLite, signer: Signer? = null): ByteArray {
        val body = frameGrpcWeb(request)
        val url = ("$BASE_URL$servicePath/$method").toHttpUrl()

        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(GRPC_WEB_MEDIA_TYPE))
            .addHeader("Accept", "application/grpc-web")
            .addHeader("X-Grpc-Web", "1")

        if (signer != null) {
            try {
                signRequest(builder, body, url.encodedPath, url.host, signer)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sign request, proceeding unsigned", e)
            }
        }

        val httpRequest = builder.build()

        httpClient.newCall(httpRequest).execute().use { response ->
            val responseBytes = response.body.bytes()
            val status = extractGrpcStatus(response, responseBytes)
            if (status != 0) throw GrpcWebException(status, extractGrpcMessage(response, responseBytes))
            if (!response.isSuccessful) throw IOException("HTTP error: ${response.code}")
            return responseBytes
        }
    }

    /**
     * Adds RFC 9421 HTTP Message Signature headers to [builder] for a request whose body is
     * [body], whose URL path is [path] and authority is [authority], signed by [signer].
     */
    private fun signRequest(
        builder: Request.Builder,
        body: ByteArray,
        path: String,
        authority: String,
        signer: Signer
    ) {
        val publicKeyDer = signer.publicKeyDer
        if (publicKeyDer.isEmpty()) return

        val bodySha256 = MessageDigest.getInstance("SHA-256").digest(body)
        val contentDigest = "sha-256=:${base64(bodySha256)}:"

        val thumbprintBytes = MessageDigest.getInstance("SHA-256").digest(publicKeyDer)
        val keyId = base64Url(thumbprintBytes)

        val created = System.currentTimeMillis() / 1000

        val sigInputValue = "(\"@method\" \"@path\" \"@authority\" \"content-digest\")" +
                ";alg=\"ecdsa-p256-sha256\"" +
                ";keyid=\"$keyId\"" +
                ";created=$created"

        val sigBase = "\"@method\": POST\n" +
                "\"@path\": $path\n" +
                "\"@authority\": $authority\n" +
                "\"content-digest\": $contentDigest\n" +
                "\"@signature-params\": $sigInputValue"

        val signature = signer.sign(sigBase.toByteArray()) ?: return

        builder
            .header("content-digest", contentDigest)
            .header("signature-input", "sig1=$sigInputValue")
            .header("signature", "sig1=:${base64(signature)}:")
    }

    fun frameGrpcWeb(message: MessageLite): ByteArray {
        val msgBytes = message.toByteArray()
        val buf = ByteBuffer.allocate(5 + msgBytes.size)
        buf.put(0x00.toByte())
        buf.putInt(msgBytes.size)
        buf.put(msgBytes)
        return buf.array()
    }

    @Throws(IOException::class)
    fun parseGrpcWebDataFrame(responseBytes: ByteArray): ByteArray {
        if (responseBytes.size < 5) throw IOException("gRPC-Web response too short: ${responseBytes.size}")
        val buf = ByteBuffer.wrap(responseBytes)
        val flags = buf.get()
        val length = buf.int
        if ((flags.toInt() and 0x80) != 0) throw IOException("Expected data frame, got trailer frame")
        if (responseBytes.size < 5 + length) throw IOException("gRPC-Web response truncated")
        val msgBytes = ByteArray(length)
        buf.get(msgBytes)
        return msgBytes
    }

    private fun extractGrpcStatus(response: okhttp3.Response, responseBytes: ByteArray): Int {
        val header = response.header("grpc-status")
        if (header != null) return header.toInt()
        var offset = 0
        while (offset + 5 <= responseBytes.size) {
            val flags = responseBytes[offset]
            val len = ByteBuffer.wrap(responseBytes, offset + 1, 4).int
            if ((flags.toInt() and 0x80) != 0 && offset + 5 + len <= responseBytes.size) {
                val trailers = String(responseBytes, offset + 5, len)
                for (line in trailers.split("\r\n")) {
                    if (line.startsWith("grpc-status:")) {
                        return line.substring("grpc-status:".length).trim().toInt()
                    }
                }
            }
            offset += 5 + len
        }
        if (response.isSuccessful) return 0
        // Server returned an HTTP error before the gRPC handler could attach a status. Map
        // common HTTP status codes to their gRPC equivalents (per the gRPC-Web spec) so
        // callers get a meaningful gRPC status instead of UNKNOWN.
        return httpStatusToGrpcStatus(response.code)
    }

    /**
     * Map an HTTP status code to a gRPC status code per the gRPC over HTTP/2 spec
     * (https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md).
     */
    private fun httpStatusToGrpcStatus(httpStatus: Int): Int {
        return when (httpStatus) {
            400 -> 3   // INVALID_ARGUMENT
            401 -> 16  // UNAUTHENTICATED
            403 -> 7   // PERMISSION_DENIED
            404 -> 5   // NOT_FOUND
            429 -> 8   // RESOURCE_EXHAUSTED
            502, 503, 504 -> 14  // UNAVAILABLE
            else -> 2  // UNKNOWN
        }
    }

    private fun extractGrpcMessage(response: okhttp3.Response, responseBytes: ByteArray): String {
        val header = response.header("grpc-message")
        if (header != null) return header
        var offset = 0
        while (offset + 5 <= responseBytes.size) {
            val flags = responseBytes[offset]
            val len = ByteBuffer.wrap(responseBytes, offset + 1, 4).int
            if ((flags.toInt() and 0x80) != 0 && offset + 5 + len <= responseBytes.size) {
                val trailers = String(responseBytes, offset + 5, len)
                for (line in trailers.split("\r\n")) {
                    if (line.startsWith("grpc-message:")) {
                        return line.substring("grpc-message:".length).trim()
                    }
                }
            }
            offset += 5 + len
        }
        return ""
    }

    private fun base64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun base64Url(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
