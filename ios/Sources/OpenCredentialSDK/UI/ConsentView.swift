import SwiftUI

/// Wrapper to make an invite code identifiable for sheet presentation.
public struct OCInviteCodeWrapper: Identifiable
{
    public let id: String
    public init(id: String) { self.id = id }
}

/// Deeplink entry point: shows org details and asks for consent.
public struct OCConsentView: View
{
    var inviteCode: String
    var onProceed: ((_ inviteCode: String, _ orgName: String, _ orgId: String) -> Void)?
    var onCancel: (() -> Void)?

    @Environment(\.dismiss) private var dismiss
    @StateObject private var vm: OCConsentViewModel

    public init(
        inviteCode: String,
        onProceed: ((_ inviteCode: String, _ orgName: String, _ orgId: String) -> Void)? = nil,
        onCancel: (() -> Void)? = nil
    )
    {
        self.inviteCode = inviteCode
        self.onProceed  = onProceed
        self.onCancel   = onCancel
        _vm = StateObject(wrappedValue: OCConsentViewModel(inviteCode: inviteCode))
    }

    public init(
        inviteCode: OCInviteCodeWrapper,
        onProceed: @escaping (_ inviteCode: String, _ orgName: String, _ orgId: String) -> Void,
        onCancel: @escaping () -> Void
    )
    {
        self.inviteCode = inviteCode.id
        self.onProceed  = onProceed
        self.onCancel   = onCancel
        _vm = StateObject(wrappedValue: OCConsentViewModel(inviteCode: inviteCode.id))
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
                    if let org = vm.organization
                    {
                        ScrollView
                        {
                            VStack(spacing: 0)
                            {
                                orgContent(org: org)
                                    .padding(.horizontal, 28)
                                    .padding(.vertical, 24)
                            }
                        }
                    }

                case .error(let msg):
                    VStack(spacing: 16)
                    {
                        Text(msg).foregroundColor(.red).multilineTextAlignment(.center)
                        Button(OCStrings.localized("oc_retry")) { vm.load() }
                    }
                    .padding()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            bottomButtons
        }
        .onAppear { vm.load() }
    }

    // MARK: - Content

    private func orgContent(org: OCOrganization) -> some View
    {
        VStack(alignment: .center, spacing: 20)
        {
            Image(systemName: "shield.checkered")
                .font(.system(size: 60))
                .foregroundColor(.accentColor)

            Text(OCStrings.localized("oc_consent_permission", org.name))
                .font(.headline)
                .multilineTextAlignment(.center)

            Divider()

            Text(OCStrings.localized("oc_consent_sharing_info", org.name))
                .font(.footnote)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            VStack(alignment: .leading, spacing: 12)
            {
                dataRow(icon: "envelope", label: OCStrings.localized("oc_consent_data_email"))
                dataRow(icon: "phone", label: OCStrings.localized("oc_consent_data_phone"))
                dataRow(icon: "qrcode", label: OCStrings.localized("oc_consent_data_identifiers"))
            }

            Text(OCStrings.localized("oc_consent_disclaimer"))
                .font(.caption)
                .foregroundColor(.secondary)
                .italic()
                .multilineTextAlignment(.center)

            orgCard(org: org)
        }
    }

    private func dataRow(icon: String, label: String) -> some View
    {
        HStack(spacing: 12)
        {
            Image(systemName: icon)
                .frame(width: 28)
                .foregroundColor(.secondary)
            Text(label)
                .font(.body)
        }
    }

    private func orgCard(org: OCOrganization) -> some View
    {
        HStack(alignment: .top, spacing: 12)
        {
            Image(systemName: "building.2")
                .font(.title2)
                .foregroundColor(.secondary)

            VStack(alignment: .leading, spacing: 4)
            {
                Text(org.name).bold()
                if !org.contactAddress.isEmpty
                {
                    Text(org.contactAddress).font(.caption).foregroundColor(.secondary)
                }
                if !org.contactPhone.isEmpty
                {
                    Text(org.contactPhone).font(.caption).foregroundColor(.secondary)
                }
                Text(org.contactEmail).font(.caption).foregroundColor(.secondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.3)))
    }

    // MARK: - Bottom Buttons

    private var bottomButtons: some View
    {
        VStack(spacing: 8)
        {
            Button(OCStrings.localized("oc_consent_proceed"))
            {
                guard let org = vm.organization else { return }
                onProceed?(inviteCode, org.name, org.organizationId)
            }
            .buttonStyle(OCPrimaryButtonStyle())
            .disabled(vm.organization == nil)

            Button(OCStrings.localized("oc_cancel"))
            {
                dismiss()
                onCancel?()
            }
            .frame(maxWidth: .infinity)
            .padding()
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.accentColor))
            .foregroundColor(.accentColor)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .background(Color(.systemBackground).shadow(radius: 1))
    }
}
