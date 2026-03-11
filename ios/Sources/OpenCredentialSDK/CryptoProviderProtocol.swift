import Foundation
import CryptoKit

/// Internal crypto helpers used by the gRPC-Web signing layer.
enum OCCrypto
{
    static func exportPublicKey() throws -> P256.Signing.PublicKey
    {
        guard let key = OpenCredentialSDK.shared.publicKey else
        {
            throw OCCryptoError.keysNotInitialized
        }
        return key
    }

    static func exportPrivateKey() throws -> P256.Signing.PrivateKey
    {
        guard let key = OpenCredentialSDK.shared.privateKey else
        {
            throw OCCryptoError.keysNotInitialized
        }
        return key
    }
}

enum OCCryptoError: Error
{
    case keysNotInitialized
}
