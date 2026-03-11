import SwiftUI

/// Shows email credentials from the server and lets the user approve which to share.
public struct OCCredentialSelectionView: View
{
    var organizationName: String = ""
    var organizationId: String = ""
    var inviteCode: String = ""
    var onApprove: (([OCCredential]) -> Void)?

    @StateObject private var vm = OCCredentialSelectionViewModel()
    @State private var isSharing = false
    @Environment(\.dismiss) private var dismiss

    public init(
        organizationName: String = "",
        organizationId: String = "",
        inviteCode: String = "",
        onApprove: (([OCCredential]) -> Void)? = nil
    )
    {
        self.organizationName = organizationName
        self.organizationId = organizationId
        self.inviteCode = inviteCode
        self.onApprove = onApprove
    }

    public var body: some View
    {
        VStack(spacing: 0)
        {
            switch vm.loadState
            {
                case .loading:
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                case .loaded:
                    ScrollView
                    {
                        VStack(alignment: .leading, spacing: 0)
                        {
                            header

                            Divider().padding(.vertical, 8)

                            if vm.credentials.isEmpty
                            {
                                Text(OCStrings.localized("oc_credentials_empty"))
                                    .foregroundColor(.secondary)
                                    .padding(.vertical, 8)
                            }
                            else
                            {
                                credentialList
                            }

                            addNewEmailButton
                        }
                        .padding(.horizontal, 24)
                        .padding(.top, 24)
                    }

                case .error(let msg):
                    VStack(spacing: 16)
                    {
                        Text(msg).foregroundColor(.red)
                        Button(OCStrings.localized("oc_retry")) { vm.load() }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            bottomButtons
        }
        .sheet(isPresented: $vm.showLoginSheet)
        {
            OCLoginView()
            {
                vm.showLoginSheet = false
                vm.load()
            }
        }
        .onAppear
        {
            vm.organizationName = organizationName
            vm.load()
        }
    }

    // MARK: - Subviews

    private var header: some View
    {
        VStack(spacing: 8)
        {
            HStack(spacing: 12)
            {
                Image(systemName: "shield.checkered")
                    .font(.system(size: 40))
                    .foregroundColor(.accentColor)

                Text("– – – >")
                    .foregroundColor(Color(.systemGray4))
                    .font(.system(size: 16))

                Image(systemName: "building.2")
                    .font(.system(size: 32))
                    .foregroundColor(.secondary)
            }

            if !organizationName.isEmpty
            {
                Text(OCStrings.localized("oc_credentials_permission", organizationName))
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)
            }

            Text(OCStrings.localized("oc_credentials_subtitle"))
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.bottom, 8)
    }

    private var credentialList: some View
    {
        VStack(alignment: .leading, spacing: 0)
        {
            Divider()
            ForEach(vm.credentials.indices, id: \.self)
            { index in
                HStack
                {
                    Image(systemName: vm.checkedIndices.contains(index) ? "checkmark.square.fill" : "square")
                        .foregroundColor(.accentColor)
                        .font(.title3)
                    Text(vm.label(for: index))
                        .font(.body)
                    Spacer()
                }
                .contentShape(Rectangle())
                .onTapGesture { vm.toggle(index: index) }
                .padding(.vertical, 14)

                Divider()
            }
        }
    }

    private var addNewEmailButton: some View
    {
        Button
        {
            vm.showLoginSheet = true
        }
        label:
        {
            HStack(spacing: 8)
            {
                Image(systemName: "plus.circle.fill")
                Text(OCStrings.localized("oc_credentials_add_email"))
            }
            .font(.callout)
            .foregroundColor(.accentColor)
        }
        .padding(.top, 12)
    }

    private var hasOrgContext: Bool
    {
        !organizationId.isEmpty && !inviteCode.isEmpty
    }

    private var bottomButtons: some View
    {
        VStack(spacing: 8)
        {
            if hasOrgContext
            {
                Button(OCStrings.localized("oc_approve"))
                {
                    isSharing = true
                    let selected = vm.selectedCredentials()
                    Task
                    {
                        do
                        {
                            for cred in selected
                            {
                                if let identity = cred.identity
                                {
                                    try await OCOrganizationService.shared.shareCredentialWithOrganization(
                                        organizationId: organizationId,
                                        identity: identity,
                                        inviteCode: inviteCode
                                    )
                                }
                            }
                            vm.saveSelectedCredentials()
                            isSharing = false
                            onApprove?(selected)
                        }
                        catch
                        {
                            isSharing = false
                        }
                    }
                }
                .buttonStyle(OCPrimaryButtonStyle())
                .disabled(!vm.anyChecked || isSharing)
            }

            Button(OCStrings.localized("oc_cancel")) { dismiss() }
                .foregroundColor(.accentColor)
                .padding()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .background(Color(.systemBackground).shadow(radius: 1))
    }
}
