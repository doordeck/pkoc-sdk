package com.sentryinteractive.opencredential.sdk.grpc

import com.sentryinteractive.opencredential.api.common.Identity
import com.sentryinteractive.opencredential.api.organization.GetOrganizationByIdRequest
import com.sentryinteractive.opencredential.api.organization.GetOrganizationByInviteCodeRequest
import com.sentryinteractive.opencredential.api.organization.Organization
import com.sentryinteractive.opencredential.api.organization.RevokeSharedCredentialFromOrganizationRequest
import com.sentryinteractive.opencredential.api.organization.ShareCredentialWithOrganizationRequest
import com.sentryinteractive.opencredential.sdk.Signer
import java.io.IOException

/**
 * gRPC-Web client for the OrganizationService.
 */
class OrganizationService {

    companion object {
        private const val SERVICE_PATH =
            "/com.sentryinteractive.opencredential.organization.v1alpha.OrganizationService"
    }

    private val client: GrpcWebClient = GrpcWebClient.getInstance()

    @Throws(IOException::class, GrpcWebException::class)
    fun getOrganizationByInviteCode(inviteCode: String): Organization {
        // Anonymous: consent screen runs before any credential is registered.
        val request = GetOrganizationByInviteCodeRequest.newBuilder()
            .setInviteCode(inviteCode)
            .build()

        val responseBytes = client.call(SERVICE_PATH, "GetOrganizationByInviteCode", request)
        val msgBytes = client.parseGrpcWebDataFrame(responseBytes)
        return Organization.parseFrom(msgBytes)
    }

    /**
     * Look up an organization by its server-assigned id. The server requires authentication
     * for this endpoint (it returns the contact details of an arbitrary org by id, so it's
     * gated on the caller proving they have a credential). Pass [signer] to authenticate;
     * leaving it null preserves backwards compatibility with older callers but will fail
     * with `UNAUTHENTICATED` on current backends.
     */
    @Throws(IOException::class, GrpcWebException::class)
    fun getOrganizationById(organizationId: String, signer: Signer? = null): Organization {
        val request = GetOrganizationByIdRequest.newBuilder()
            .setOrganizationId(organizationId)
            .build()

        val responseBytes = client.call(SERVICE_PATH, "GetOrganizationById", request, signer)
        val msgBytes = client.parseGrpcWebDataFrame(responseBytes)
        return Organization.parseFrom(msgBytes)
    }

    @Throws(IOException::class, GrpcWebException::class)
    fun shareCredentialWithOrganization(
        signer: Signer,
        organizationId: String,
        identity: Identity,
        inviteCode: String
    ) {
        val request = ShareCredentialWithOrganizationRequest.newBuilder()
            .setOrganizationId(organizationId)
            .setIdentity(identity)
            .setInviteCode(inviteCode)
            .build()

        client.call(SERVICE_PATH, "ShareCredentialWithOrganization", request, signer)
    }

    @Throws(IOException::class, GrpcWebException::class)
    fun revokeSharedCredentialFromOrganization(
        signer: Signer,
        organizationId: String,
        identity: Identity?
    ) {
        val builder = RevokeSharedCredentialFromOrganizationRequest.newBuilder()
            .setOrganizationId(organizationId)

        if (identity != null) {
            builder.setIdentity(identity)
        }

        client.call(SERVICE_PATH, "RevokeSharedCredentialFromOrganization", builder.build(), signer)
    }
}
