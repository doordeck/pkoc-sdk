import Foundation
import Security

public enum OCKeyStoreError: Error
{
    case keyNotFound
    case saveFailed(OSStatus)
    case loadFailed(OSStatus)
    case deleteFailed(OSStatus)
}

public final class OCKeyStore
{
    private static let service = "com.sentryinteractive.opencredential"
    private static let account = "p256-signing-key"

    public static func save(privateKeyData: Data) throws
    {
        // Delete any existing item first
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(deleteQuery as CFDictionary)

        let addQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: privateKeyData,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]

        let status = SecItemAdd(addQuery as CFDictionary, nil)
        guard status == errSecSuccess else
        {
            throw OCKeyStoreError.saveFailed(status)
        }
    }

    public static func load() throws -> Data
    {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else
        {
            if status == errSecItemNotFound
            {
                throw OCKeyStoreError.keyNotFound
            }
            throw OCKeyStoreError.loadFailed(status)
        }

        return data
    }

    public static func delete() throws
    {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]

        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else
        {
            throw OCKeyStoreError.deleteFailed(status)
        }
    }
}
