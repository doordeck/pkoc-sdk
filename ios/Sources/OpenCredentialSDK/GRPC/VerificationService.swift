import Foundation
import CryptoKit

/// Singleton wrapper around the VerificationService gRPC-Web RPCs.
public final class OCVerificationService
{
    public static let shared = OCVerificationService()

    private let servicePath =
        "/com.sentryinteractive.opencredential.verification.v1alpha.VerificationService"

    private let client = GrpcWebClient.shared

    private init() {}

    public func startEmailVerification(
        email: String,
        credential: Data,
        credentialType: OCCredentialType
    ) async throws -> OCStartEmailVerificationResponse
    {
        let body = encodeStartEmailVerificationRequest(
            email: email,
            credential: credential,
            credentialType: credentialType
        )
        let responseData = try await client.call(
            servicePath: servicePath,
            method: "StartEmailVerification",
            body: body
        )
        let msgData = try client.parseDataFrame(responseData)
        return decodeStartEmailVerificationResponse(msgData)
    }

    public func completeEmailVerification(token: String, code: String) async throws
    {
        let body = encodeCompleteEmailVerificationRequest(token: token, code: code)
        _ = try await client.call(
            servicePath: servicePath,
            method: "CompleteEmailVerification",
            body: body
        )
    }
}
