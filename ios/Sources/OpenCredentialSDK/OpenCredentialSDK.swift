import Foundation
import CryptoKit
import SwiftUI

/// Main entry point for the OpenCredential SDK.
public final class OpenCredentialSDK
{
    public static let shared = OpenCredentialSDK()

    private(set) public var privateKey: P256.Signing.PrivateKey?
    private(set) public var publicKey: P256.Signing.PublicKey?

    private init() {}

    /// Initialize the SDK with a P256 signing key pair.
    public func initialize(privateKey: P256.Signing.PrivateKey, publicKey: P256.Signing.PublicKey)
    {
        self.privateKey = privateKey
        self.publicKey = publicKey
        verifyOnServer()
    }

    // Fire and forget — updates the server's last-seen timestamp for this credential
    private func verifyOnServer()
    {
        Task { try? await OCCredentialService.shared.verifyCredential() }
    }

    /// Generate and store a new P256 key pair, persisting to the Keychain.
    public func generateKeys()
    {
        let key = P256.Signing.PrivateKey()
        self.privateKey = key
        self.publicKey = key.publicKey
        try? OCKeyStore.save(privateKeyData: key.rawRepresentation)
    }

    /// Load keys from Keychain. Returns true if keys were loaded.
    @discardableResult
    public func loadStoredKeys() -> Bool
    {
        do
        {
            let data = try OCKeyStore.load()
            let priv = try P256.Signing.PrivateKey(rawRepresentation: data)
            self.privateKey = priv
            self.publicKey  = priv.publicKey
            return true
        }
        catch
        {
            return false
        }
    }

    /// Returns the root SwiftUI view that handles the full SDK flow.
    @ViewBuilder
    public func rootView(
        onCompleted: @escaping ([OCCredential]) -> Void,
        onCancelled: @escaping () -> Void
    ) -> some View
    {
        OpenCredentialRootView(
            onCompleted: onCompleted,
            onCancelled: onCancelled
        )
    }
}
