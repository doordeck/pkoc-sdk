import Foundation

/// Singleton wrapper around the CredentialService gRPC-Web RPCs.
public final class OCCredentialService
{
    public static let shared = OCCredentialService()

    private let servicePath =
        "/com.sentryinteractive.opencredential.credential.v1alpha.CredentialService"

    private let client = GrpcWebClient.shared

    private init() {}

    public func getCredentials(filter: OCCredentialFilter = .sameKey) async throws -> OCGetCredentialsResponse
    {
        let body = encodeGetCredentialsRequest(filter: filter)
        let responseData = try await client.call(
            servicePath: servicePath,
            method: "GetCredentials",
            body: body
        )
        let msgData = try client.parseDataFrame(responseData)
        return decodeGetCredentialsResponse(msgData)
    }

    public func deleteCredentials(email: String? = nil, keyThumbprint: String? = nil) async throws
    {
        let body = encodeDeleteCredentialsRequest(email: email, keyThumbprint: keyThumbprint)
        _ = try await client.call(
            servicePath: servicePath,
            method: "DeleteCredentials",
            body: body
        )
    }

    public func verifyCredential() async throws
    {
        _ = try await client.call(
            servicePath: servicePath,
            method: "VerifyCredential",
            body: Data()
        )
    }
}
