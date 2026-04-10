package com.sentryinteractive.opencredential.sdk.grpc

import com.google.protobuf.Empty
import com.sentryinteractive.opencredential.api.credential.CredentialFilter
import com.sentryinteractive.opencredential.api.credential.DeleteCredentialsRequest
import com.sentryinteractive.opencredential.api.credential.GetCredentialsRequest
import com.sentryinteractive.opencredential.api.credential.GetCredentialsResponse
import com.sentryinteractive.opencredential.sdk.OCIdentity
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
    fun getCredentials(filter: CredentialFilter): GetCredentialsResponse {
        val request = GetCredentialsRequest.newBuilder()
            .setFilter(filter)
            .build()

        val responseBytes = client.call(SERVICE_PATH, "GetCredentials", request)
        val msgBytes = client.parseGrpcWebDataFrame(responseBytes)
        return GetCredentialsResponse.parseFrom(msgBytes)
    }

    @Throws(IOException::class, GrpcWebException::class)
    fun deleteCredentials(identity: OCIdentity? = null, keyThumbprint: String? = null) {
        val builder = DeleteCredentialsRequest.newBuilder()
        if (identity != null) {
            builder.setIdentity(identity.toProto())
        }
        if (keyThumbprint != null) {
            builder.setKeyThumbprint(keyThumbprint)
        }
        client.call(SERVICE_PATH, "DeleteCredentials", builder.build())
    }

    @Throws(IOException::class, GrpcWebException::class)
    fun verifyCredential() {
        client.call(SERVICE_PATH, "VerifyCredential", Empty.getDefaultInstance())
    }
}
