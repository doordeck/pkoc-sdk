package com.sentryinteractive.opencredential.sdk.grpc

import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import com.sentryinteractive.opencredential.api.common.CredentialType
import com.sentryinteractive.opencredential.api.verification.CompleteEmailVerificationRequest
import com.sentryinteractive.opencredential.api.verification.DeviceAttestation
import com.sentryinteractive.opencredential.api.verification.GetAttestationChallengeResponse
import com.sentryinteractive.opencredential.api.verification.StartEmailVerificationRequest
import com.sentryinteractive.opencredential.api.verification.StartEmailVerificationResponse
import com.sentryinteractive.opencredential.sdk.Signer
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
    fun getAttestationChallenge(): GetAttestationChallengeResponse {
        // Bootstrap call: not signed (the credential key doesn't exist yet at this point).
        val responseBytes = client.call(SERVICE_PATH, "GetAttestationChallenge", Empty.getDefaultInstance())
        val msgBytes = client.parseGrpcWebDataFrame(responseBytes)
        return GetAttestationChallengeResponse.parseFrom(msgBytes)
    }

    @Throws(IOException::class, GrpcWebException::class)
    fun startEmailVerification(
        signer: Signer,
        email: String,
        credential: ByteArray,
        credentialType: CredentialType,
        attestation: DeviceAttestation? = null
    ): StartEmailVerificationResponse {
        val builder = StartEmailVerificationRequest.newBuilder()
            .setEmail(email)
            .setCredential(ByteString.copyFrom(credential))
            .setCredentialType(credentialType)

        if (attestation != null) {
            builder.setAttestation(attestation)
        }

        val responseBytes = client.call(SERVICE_PATH, "StartEmailVerification", builder.build(), signer)
        val msgBytes = client.parseGrpcWebDataFrame(responseBytes)
        return StartEmailVerificationResponse.parseFrom(msgBytes)
    }

    @Throws(IOException::class, GrpcWebException::class)
    fun completeEmailVerification(signer: Signer, token: String, code: String) {
        val request = CompleteEmailVerificationRequest.newBuilder()
            .setToken(token)
            .setCode(code)
            .build()

        client.call(SERVICE_PATH, "CompleteEmailVerification", request, signer)
    }
}
