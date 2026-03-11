import Foundation
import CryptoKit

@MainActor
internal final class OCLoginViewModel: ObservableObject
{
    enum Step { case email, code }

    @Published var step: Step = .email
    @Published var email: String = ""
    @Published var code: String = ""
    @Published var isLoading = false
    @Published var emailError: String? = nil
    @Published var codeError: String? = nil
    @Published var codeStatus: String? = nil

    var onSuccess: (() -> Void)?

    private var verificationToken = ""

    func sendCode()
    {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        guard isValidEmail(trimmed) else
        {
            emailError = OCStrings.localized("oc_email_invalid")
            return
        }

        isLoading = true
        emailError = nil
        codeError  = nil
        codeStatus = nil

        Task
        {
            do
            {
                let derKey = try OCCrypto.exportPublicKey().derRepresentation

                let response = try await OCVerificationService.shared.startEmailVerification(
                    email: trimmed,
                    credential: derKey,
                    credentialType: .p256,
                    attestationDocument: "TBD"
                )
                verificationToken = response.verificationToken

                isLoading  = false
                step       = .code
                codeStatus = OCStrings.localized("oc_code_sent")
            }
            catch let error as GrpcWebError
            {
                isLoading  = false
                emailError = OCStrings.localized("oc_error_network_detail", error.statusName)
            }
            catch
            {
                isLoading  = false
                emailError = OCStrings.localized("oc_error_network")
            }
        }
    }

    func verifyCode()
    {
        let trimmed = code.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else
        {
            codeError = OCStrings.localized("oc_code_required")
            return
        }

        isLoading  = true
        codeError  = nil
        codeStatus = nil

        Task
        {
            do
            {
                try await OCVerificationService.shared.completeEmailVerification(
                    token: verificationToken,
                    code: trimmed
                )
                isLoading = false
                onSuccess?()
            }
            catch let error as GrpcWebError
            {
                isLoading = false
                switch error.statusCode
                {
                    case 3, 5:
                        codeError = OCStrings.localized("oc_code_invalid")
                    case 4:
                        codeError = OCStrings.localized("oc_code_expired")
                    default:
                        codeError = OCStrings.localized("oc_error_network_detail", error.statusName)
                }
            }
            catch
            {
                isLoading  = false
                codeError  = OCStrings.localized("oc_error_network")
            }
        }
    }

    func resendCode()
    {
        step      = .email
        codeError = nil
        codeStatus = nil
        sendCode()
    }

    private func isValidEmail(_ value: String) -> Bool
    {
        let pattern = "[A-Z0-9a-z._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"
        return value.range(of: pattern, options: .regularExpression) != nil
    }
}
