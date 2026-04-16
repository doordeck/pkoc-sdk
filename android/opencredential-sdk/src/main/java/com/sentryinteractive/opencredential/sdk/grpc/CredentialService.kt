package com.sentryinteractive.opencredential.sdk.grpc

import com.google.protobuf.Empty
import com.sentryinteractive.opencredential.api.credential.CredentialFilter
import com.sentryinteractive.opencredential.api.credential.DeleteCredentialsRequest
import com.sentryinteractive.opencredential.api.credential.GetCredentialsRequest
import com.sentryinteractive.opencredential.api.credential.GetCredentialsResponse
import com.sentryinteractive.opencredential.sdk.OCIdentity
import com.sentryinteractive.opencredential.sdk.Signer
import java.io.IOException

/**
 * gRPC-Web client for the CredentialService.
 */
class CredentialService {

    companion object {
        private const val SERVICE_PATH =
            "/com.sentryinteractive.opencredential.credential.v1alpha.CredentialService"
    }

    private val client: GrpcWebClient = GrpcWebClient.getInstance()

    @Throws(IOException::class, GrpcWebException::class)
    fun getCredentials(signer: Signer, filter: CredentialFilter): GetCredentialsResponse {
        val request = GetCredentialsRequest.newBuilder()
            .setFilter(filter)
            .build()

        val responseBytes = client.call(SERVICE_PATH, "GetCredentials", request, signer)
        val msgBytes = client.parseGrpcWebDataFrame(responseBytes)
        return GetCredentialsResponse.parseFrom(msgBytes)
    }

    @Throws(IOException::class, GrpcWebException::class)
    fun deleteCredentials(signer: Signer, identity: OCIdentity? = null) {
        val builder = DeleteCredentialsRequest.newBuilder()
        if (identity != null) {
            builder.setIdentity(identity.toProto())
        }
        client.call(SERVICE_PATH, "DeleteCredentials", builder.build(), signer)
    }

    @Throws(IOException::class, GrpcWebException::class)
    fun verifyCredential(signer: Signer) {
        client.call(SERVICE_PATH, "VerifyCredential", Empty.getDefaultInstance(), signer)
    }
}
