import SwiftUI
import OpenCredentialSDK

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

    var body: some View
    {
        NavigationView
        {
            VStack(spacing: 0)
            {
                headerSection

                Divider().padding(.vertical, 16)

                buttonSection

                Spacer()

                statusSection
            }
            .padding(.horizontal, 24)
            .navigationTitle("OC SDK Sample")
            .navigationBarTitleDisplayMode(.inline)
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
                    }
                )
            }
            .sheet(isPresented: $showLogin)
            {
                OCLoginView(returnOnSuccess: true)
                {
                    showLogin = false
                    statusMessage = "Login successful! Select credentials to share..."
                    showCredentialSelection = true
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

    // MARK: - Sections

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
                .autocapitalization(.none)
                .disableAutocorrection(true)
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
            .disabled(inviteCodeInput.isEmpty)
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
