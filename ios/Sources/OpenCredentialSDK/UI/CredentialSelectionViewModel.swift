import Foundation

@MainActor
internal final class OCCredentialSelectionViewModel: ObservableObject
{
    enum LoadState { case loading, loaded, error(String) }

    @Published var loadState: LoadState = .loading
    @Published var credentials: [OCCredential] = []
    @Published var checkedIndices: Set<Int> = []
    @Published var showLoginSheet = false

    var organizationName: String = ""

    var anyChecked: Bool { !checkedIndices.isEmpty }

    func load()
    {
        loadState = .loading

        Task
        {
            do
            {
                let response = try await OCCredentialService.shared.getCredentials(filter: .sameKey)
                let emailCreds = response.credentials.filter
                {
                    if case .email = $0.identity?.identityCase { return true }
                    return false
                }
                credentials    = emailCreds
                checkedIndices = Set(emailCreds.indices)
                loadState      = .loaded
            }
            catch
            {
                credentials    = []
                checkedIndices = []
                loadState      = .loaded
            }
        }
    }

    func toggle(index: Int)
    {
        if checkedIndices.contains(index)
        {
            checkedIndices.remove(index)
        }
        else
        {
            checkedIndices.insert(index)
        }
    }

    func label(for index: Int) -> String
    {
        let cred  = credentials[index]
        let email = cred.identity?.email ?? ""
        let hasDuplicate = credentials.filter { $0.identity?.email == email }.count > 1
        if hasDuplicate
        {
            let hex = cred.credentialHex
            let suffix = hex.suffix(6)
            return "\(email)  (…\(suffix))"
        }
        return email
    }

    func selectedCredentials() -> [OCCredential]
    {
        checkedIndices.sorted().compactMap
        {
            index in index < credentials.count ? credentials[index] : nil
        }
    }

    func saveSelectedCredentials()
    {
        let hexIds = selectedCredentials().map { $0.credentialHex }
        OCCredentialStore.save(hexIds: hexIds)
    }
}
