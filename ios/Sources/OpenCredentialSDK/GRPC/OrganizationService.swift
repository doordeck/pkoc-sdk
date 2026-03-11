import Foundation

/// Singleton wrapper around the OrganizationService gRPC-Web RPCs.
public final class OCOrganizationService
{
    public static let shared = OCOrganizationService()

    private let servicePath =
        "/com.sentryinteractive.opencredential.organization.v1alpha.OrganizationService"

    private let client = GrpcWebClient.shared

    private init() {}

    public func getOrganizationByInviteCode(_ inviteCode: String) async throws -> OCOrganization
    {
        let body = encodeGetOrganizationByInviteCodeRequest(inviteCode: inviteCode)
        let responseData = try await client.call(
            servicePath: servicePath,
            method: "GetOrganizationByInviteCode",
            body: body
        )
        let msgData = try client.parseDataFrame(responseData)
        return decodeOCOrganization(msgData)
    }

    public func shareCredentialWithOrganization(
        organizationId: String,
        identity: OCIdentity,
        inviteCode: String
    ) async throws
    {
        let body = encodeShareCredentialWithOrganizationRequest(
            organizationId: organizationId,
            identity: identity,
            inviteCode: inviteCode
        )
        _ = try await client.call(
            servicePath: servicePath,
            method: "ShareCredentialWithOrganization",
            body: body
        )
    }
}
