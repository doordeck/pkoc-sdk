import SwiftUI
import OpenCredentialSDK

private enum DeleteMode
{
    case identity
    case identityAndKey
}

struct ContentView: View
{
    @State private var statusMessage = "Ready"
    @State private var showConsent = false
    @State private var showLogin = false
    @State private var showCredentialSelection = false
    @State private var credentialCount = 0

    @State private var inviteCodeInput = ""

    @State private var pendingOrgId = ""
    @State private var pendingOrgName = ""
    @State private var pendingInviteCode = ""

    @State private var identities: [OCIdentity] = []
    @State private var hasCredentials = false
    @State private var isLoading = false
    @State private var showDeleteSheet = false
    @State private var showIdentityPicker = false
    @State private var identityPickerMode: DeleteMode = .identity

    var body: some View
    {
        NavigationView
        {
            VStack(spacing: 0)
            {
                headerSection

                deleteButton

                Divider().padding(.vertical, 16)

                buttonSection

                Spacer()

                statusSection
            }
            .padding(.horizontal, 24)
            .navigationTitle("OC SDK Sample")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { checkCredentials() }
            .sheet(isPresented: $showDeleteSheet) { deleteSheet }
            .sheet(isPresented: $showIdentityPicker) { identityPickerSheet }
            .sheet(isPresented: $showConsent)
            {
                OCConsentView(
                    inviteCode: inviteCodeInput,
                    onProceed: { code, orgName, orgId in
                        showConsent = false
                        pendingInviteCode = code
                        pendingOrgName = orgName
                        pendingOrgId = orgId
                        statusMessage = "Consent given for \(orgName). Logging in..."
                        showLogin = true
                    },
                    onCancel: {
                        showConsent = false
                        statusMessage = "Consent cancelled"
                        checkCredentials()
                    }
                )
            }
            .sheet(isPresented: $showLogin)
            {
                OCLoginView()
                {
                    showLogin = false
                    statusMessage = "Login successful! Select credentials to share..."
                    showCredentialSelection = true
                    checkCredentials()
                }
            }
            .sheet(isPresented: $showCredentialSelection)
            {
                OCCredentialSelectionView(
                    organizationName: pendingOrgName,
                    organizationId: pendingOrgId,
                    inviteCode: pendingInviteCode,
                    onApprove: { credentials in
                        showCredentialSelection = false
                        credentialCount = credentials.count
                        statusMessage = "Flow completed! \(credentials.count) credential(s) shared."
                        clearPendingOrg()
                        checkCredentials()
                    }
                )
            }
        }
    }

    private func clearPendingOrg()
    {
        pendingOrgId = ""
        pendingOrgName = ""
        pendingInviteCode = ""
    }

    // MARK: - Credentials

    private func checkCredentials()
    {
        Task { await refreshIdentities() }
    }

    @MainActor
    private func refreshIdentities() async
    {
        isLoading = true
        defer { isLoading = false }
        do
        {
            identities = try await OpenCredentialSDK.shared.getIdentities()
            hasCredentials = !identities.isEmpty
        }
        catch
        {
            print("OC sample: getIdentities() failed: \(error)")
        }
    }

    private func deleteCredentials(identity: OCIdentity? = nil, keyThumbprint: String? = nil)
    {
        isLoading = true
        Task
        {
            do
            {
                try await OpenCredentialSDK.shared.deleteCredentials(identity: identity, keyThumbprint: keyThumbprint)
                let remainingIds = (try? await OpenCredentialSDK.shared.getIdentities()) ?? []
                await MainActor.run
                {
                    isLoading = false
                    identities = remainingIds
                    hasCredentials = !remainingIds.isEmpty
                    statusMessage = "Credentials deleted"
                }
            }
            catch
            {
                await MainActor.run
                {
                    isLoading = false
                    statusMessage = "Failed to delete credentials"
                }
            }
        }
    }

    // MARK: - Sections

    private var deleteButton: some View
    {
        Button
        {
            showDeleteSheet = true
        }
        label:
        {//2a87c473-da2f-4ac2-81ac-fb067396a458
            Group
            {
                if isLoading
                {
                    ProgressView()
                }
                else
                {
                    Text("Delete Credentials")
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.accentColor))
        }
        .disabled(!hasCredentials || isLoading)
        .padding(.top, 16)
    }

    private var deleteSheet: some View
    {
        NavigationView
        {
            List
            {
                Button("Delete All")
                {
                    showDeleteSheet = false
                    deleteCredentials()
                }

                Button("Delete by Identity")
                {
                    showDeleteSheet = false
                    identityPickerMode = .identity
                    showIdentityPicker = true
                }

                Button("Delete by Key (this device)")
                {
                    showDeleteSheet = false
                    if let thumbprint = OpenCredentialSDK.shared.getKeyThumbprint()
                    {
                        deleteCredentials(keyThumbprint: thumbprint)
                    }
                    else
                    {
                        statusMessage = "No device key available; cannot delete by key."
                    }
                }

                Button("Delete by Identity + Key")
                {
                    showDeleteSheet = false
                    identityPickerMode = .identityAndKey
                    showIdentityPicker = true
                }
            }
            .navigationTitle("Delete Credentials")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar
            {
                ToolbarItem(placement: .cancellationAction)
                {
                    Button("Cancel") { showDeleteSheet = false }
                }
            }
        }
    }

    private var identityPickerSheet: some View
    {
        NavigationView
        {
            List(identities, id: \.self)
            { identity in
                Button(identity.value)
                {
                    showIdentityPicker = false
                    switch identityPickerMode
                    {
                        case .identity:
                            deleteCredentials(identity: identity, keyThumbprint: nil)
                        case .identityAndKey:
                            guard let thumbprint = OpenCredentialSDK.shared.getKeyThumbprint() else
                            {
                                statusMessage = "Cannot delete by Identity + Key: no key is available on this device."
                                return
                            }
                            deleteCredentials(identity: identity, keyThumbprint: thumbprint)
                    }
                }
            }
            .navigationTitle("Select Identity")
            .navigationBarTitleDisplayMode(.inline)
            .task { await refreshIdentities() }
            .toolbar
            {
                ToolbarItem(placement: .cancellationAction)
                {
                    Button("Cancel") { showIdentityPicker = false }
                }
            }
        }
    }

    private var headerSection: some View
    {
        VStack(spacing: 12)
        {
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 56))
                .foregroundColor(.accentColor)
                .padding(.top, 32)

            Text("OpenCredential SDK")
                .font(.title2)
                .fontWeight(.bold)

            Text("Sample Application")
                .font(.subheadline)
                .foregroundColor(.secondary)

            Text("Demonstrates SDK integration: consent -> login -> credential selection -> share.")
                .font(.footnote)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
        }
    }

    private var buttonSection: some View
    {
        VStack(spacing: 12)
        {
            TextField("Invitation code", text: $inviteCodeInput)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding()
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.4)))

            Button(action: { showConsent = true })
            {
                Label("Launch Consent Flow", systemImage: "building.2")
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(inviteCodeInput.isEmpty ? Color.gray : Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(10)
            }
            .disabled(inviteCodeInput.isEmpty || isLoading)
        }
    }

    private var statusSection: some View
    {
        VStack(spacing: 8)
        {
            Text("Status")
                .font(.caption)
                .foregroundColor(.secondary)

            Text(statusMessage)
                .font(.callout)
                .foregroundColor(.secondary)
                .italic()
                .multilineTextAlignment(.center)

            if credentialCount > 0
            {
                Text("\(credentialCount) credential(s) shared")
                    .font(.caption)
                    .foregroundColor(.green)
            }
        }
        .padding(.bottom, 24)
    }
}
