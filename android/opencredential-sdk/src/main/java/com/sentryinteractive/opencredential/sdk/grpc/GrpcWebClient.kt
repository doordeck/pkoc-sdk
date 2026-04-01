package com.sentryinteractive.opencredential.sdk.grpc

import com.google.protobuf.MessageLite
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.ByteBuffer

class GrpcWebClient private constructor() {

    companion object {
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

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(GrpcWebInterceptor())
        .build()

    @Throws(IOException::class, GrpcWebException::class)
    fun call(servicePath: String, method: String, request: MessageLite): ByteArray {
        val body = frameGrpcWeb(request)
        val httpRequest = Request.Builder()
            .url("$BASE_URL$servicePath/$method")
            .post(body.toRequestBody(GRPC_WEB_MEDIA_TYPE))
            .addHeader("Accept", "application/grpc-web")
            .addHeader("X-Grpc-Web", "1")
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            val responseBytes = response.body.bytes()
            val status = extractGrpcStatus(response, responseBytes)
            if (status != 0) throw GrpcWebException(status, extractGrpcMessage(response, responseBytes))
            if (!response.isSuccessful) throw IOException("HTTP error: ${response.code}")
            return responseBytes
        }
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
        return if (response.isSuccessful) 0 else 2
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
}
