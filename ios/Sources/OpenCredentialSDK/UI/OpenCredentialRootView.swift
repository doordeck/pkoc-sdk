import SwiftUI

// MARK: - Root View Model

@MainActor
internal final class OCRootViewModel: ObservableObject
{
    enum AppState
    {
        case loading
        case noContext
        case main(credentials: [OCCredential])
    }

    @Published var state: AppState = .loading

    func start()
    {
        loadKeysAndRoute()
    }

    private func loadKeysAndRoute()
    {
        let sdk = OpenCredentialSDK.shared

        if sdk.publicKey == nil
        {
            if !sdk.loadStoredKeys()
            {
                sdk.generateKeys()
            }
        }

        Task { await checkSavedCredentials() }
    }

    private func checkSavedCredentials() async
    {
        let savedIds = OCCredentialStore.load()
        guard !savedIds.isEmpty else
        {
            state = .noContext
            return
        }

        do
        {
            let response = try await OCCredentialService.shared.getCredentials(filter: .sameKey)
            let matched = response.credentials.filter
            { cred in
                guard case .email = cred.identity?.identityCase else { return false }
                return savedIds.contains(cred.credentialHex)
            }

            if matched.isEmpty
            {
                OCCredentialStore.clear()
                state = .noContext
            }
            else
            {
                state = .main(credentials: matched)
            }
        }
        catch
        {
            state = .noContext
        }
    }
}

// MARK: - Root View

/// Root view that manages the SDK state (loading, no context, or credentials available).
public struct OpenCredentialRootView: View
{
    var onCompleted: ([OCCredential]) -> Void
    var onCancelled: () -> Void

    @StateObject private var vm = OCRootViewModel()
    @State private var didComplete = false

    public init(
        onCompleted: @escaping ([OCCredential]) -> Void,
        onCancelled: @escaping () -> Void
    )
    {
        self.onCompleted = onCompleted
        self.onCancelled = onCancelled
    }

    public var body: some View
    {
        Group
        {
            switch vm.state
            {
                case .loading:
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                case .noContext:
                    noContextView

                case .main(let credentials):
                    Color.clear.onAppear
                    {
                        guard !didComplete else { return }
                        didComplete = true
                        onCompleted(credentials)
                    }
            }
        }
        .onAppear { vm.start() }
    }

    private var noContextView: some View
    {
        VStack(spacing: 24)
        {
            Spacer()

            Image(systemName: "lock.shield")
                .font(.system(size: 60))
                .foregroundColor(.secondary)

            Text(OCStrings.localized("oc_no_context_title"))
                .font(.headline)

            Text(OCStrings.localized("oc_no_context_message"))
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Spacer()

            Button(OCStrings.localized("oc_cancel")) { onCancelled() }
                .foregroundColor(.accentColor)
                .padding(.bottom, 24)
        }
    }
}
