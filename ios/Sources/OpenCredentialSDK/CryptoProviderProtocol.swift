import Foundation
import CryptoKit

/// Internal crypto helpers used by the gRPC-Web signing layer.
enum OCCrypto
{
    static func exportPublicKey() -> P256.Signing.PublicKey
    {
        guard let key = OpenCredentialSDK.shared.publicKey else
        {
            fatalError("OpenCredentialSDK: publicKey not initialized. Call initialize() or generateKeys() first.")
        }
        return key
    }

    static func exportPrivateKey() -> P256.Signing.PrivateKey
    {
        guard let key = OpenCredentialSDK.shared.privateKey else
        {
            fatalError("OpenCredentialSDK: privateKey not initialized. Call initialize() or generateKeys() first.")
        }
        return key
    }
}
