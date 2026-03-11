import Foundation

@MainActor
internal final class OCConsentViewModel: ObservableObject
{
    enum LoadState: Equatable
    {
        case loading, loaded, error(String)
    }

    @Published var loadState: LoadState = .loading
    @Published var organization: OCOrganization?

    let inviteCode: String

    init(inviteCode: String)
    {
        self.inviteCode = inviteCode
    }

    func load()
    {
        loadState = .loading
        Task
        {
            do
            {
                let org = try await OCOrganizationService.shared.getOrganizationByInviteCode(inviteCode)
                organization = org
                loadState    = .loaded
            }
            catch
            {
                loadState = .error(OCStrings.localized("oc_error_load_org"))
            }
        }
    }
}
