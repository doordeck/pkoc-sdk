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

    /// Returns the list of identities (emails/phones) associated with this device's key.
    public func getIdentities() async throws -> [OCIdentity]
    {
        let response = try await OCCredentialService.shared.getCredentials(filter: .sameKey)
        let identities = response.credentials.compactMap { cred -> OCIdentity? in
            guard let identity = cred.identity else { return nil }
            if case .none = identity.identityCase { return nil }
            return identity
        }
        return Array(Set(identities))
    }

    /// Deletes credentials belonging to the authenticated user. Both fields act as optional AND filters over the full
    /// set of credentials reachable from the current authentication context:
    ///
    /// - (none)            - delete every credential across all identities (GDPR full erasure)
    /// - identity          - delete all keys for a single identity (e.g. remove an email address)
    /// - key_thumbprint    - delete a specific key across all identities (e.g. lost device)
    /// - both              - delete exactly one credential/identity combination
    ///
    /// Any approved organization shares are automatically revoked before deletion.
    public func deleteCredentials(identity: OCIdentity? = nil, keyThumbprint: String? = nil) async throws
    {
        try await OCCredentialService.shared.deleteCredentials(identity: identity, keyThumbprint: keyThumbprint)
    }

    /// Returns the base64url-encoded SHA-256 thumbprint of this device's public key.
    public func getKeyThumbprint() -> String?
    {
        guard let publicKey = publicKey else { return nil }
        let der = publicKey.derRepresentation
        let hash = SHA256.hash(data: der)
        return Data(hash).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
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
