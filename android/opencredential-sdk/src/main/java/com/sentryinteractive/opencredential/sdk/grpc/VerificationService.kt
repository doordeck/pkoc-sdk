package com.sentryinteractive.opencredential.sdk.grpc

import com.google.protobuf.ByteString
import com.sentryinteractive.opencredential.api.common.CredentialType
import com.sentryinteractive.opencredential.api.verification.CompleteEmailVerificationRequest
import com.sentryinteractive.opencredential.api.verification.StartEmailVerificationRequest
import com.sentryinteractive.opencredential.api.verification.StartEmailVerificationResponse
import java.io.IOException

/**
 * gRPC-Web client for the VerificationService.
 */
class VerificationService {

    companion object {
        private const val SERVICE_PATH =
            "/com.sentryinteractive.opencredential.verification.v1alpha.VerificationService"
    }

    private val client: GrpcWebClient = GrpcWebClient.getInstance()

    @Throws(IOException::class, GrpcWebException::class)
    fun startEmailVerification(
        email: String,
        credential: ByteArray,
        credentialType: CredentialType,
        attestationDocument: String
    ): StartEmailVerificationResponse {
        val request = StartEmailVerificationRequest.newBuilder()
            .setEmail(email)
            .setCredential(ByteString.copyFrom(credential))
            .setCredentialType(credentialType)
            .setAttestationDocument(attestationDocument)
            .build()

        val responseBytes = client.call(SERVICE_PATH, "StartEmailVerification", request)
        val msgBytes = client.parseGrpcWebDataFrame(responseBytes)
        return StartEmailVerificationResponse.parseFrom(msgBytes)
    }

    @Throws(IOException::class, GrpcWebException::class)
    fun completeEmailVerification(token: String, code: String) {
        val request = CompleteEmailVerificationRequest.newBuilder()
            .setToken(token)
            .setCode(code)
            .build()

        client.call(SERVICE_PATH, "CompleteEmailVerification", request)
    }
}
