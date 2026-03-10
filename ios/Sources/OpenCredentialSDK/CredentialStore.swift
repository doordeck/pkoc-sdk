import Foundation

public enum OCCredentialStore
{
    private static let key = "oc_selected_credential_ids"

    public static func save(hexIds: [String])
    {
        UserDefaults.standard.set(hexIds.joined(separator: ","), forKey: key)
    }

    public static func load() -> Set<String>
    {
        guard let stored = UserDefaults.standard.string(forKey: key), !stored.isEmpty else { return [] }
        return Set(stored.split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty })
    }

    public static func clear()
    {
        UserDefaults.standard.removeObject(forKey: key)
    }
}
