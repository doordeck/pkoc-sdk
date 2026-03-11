import SwiftUI

/// Email + 2FA verification screen.
public struct OCLoginView: View
{
    @StateObject private var vm = OCLoginViewModel()

    var onSuccess: (() -> Void)?

    public init(onSuccess: (() -> Void)? = nil)
    {
        self.onSuccess = onSuccess
    }

    public var body: some View
    {
        ScrollView
        {
            VStack(spacing: 0)
            {
                Image(systemName: "lock.shield")
                    .font(.system(size: 60))
                    .foregroundColor(.accentColor)
                    .padding(.top, 48)
                    .padding(.bottom, 16)

                Text(OCStrings.localized("oc_login_title"))
                    .font(.title2)
                    .bold()
                    .padding(.bottom, 32)

                if vm.step == .email
                {
                    emailStep
                }
                else
                {
                    codeStep
                }

                if vm.isLoading
                {
                    ProgressView()
                        .padding(.top, 16)
                }
            }
            .padding(.horizontal, 24)
        }
        .onAppear
        {
            vm.onSuccess = onSuccess
        }
    }

    // MARK: - Email Step

    private var emailStep: some View
    {
        VStack(alignment: .leading, spacing: 12)
        {
            TextField(OCStrings.localized("oc_email_placeholder"), text: $vm.email)
                .keyboardType(.emailAddress)
                .autocapitalization(.none)
                .textContentType(.emailAddress)
                .padding()
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.4)))

            if let error = vm.emailError
            {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
            }

            Button(OCStrings.localized("oc_send_code"))
            {
                vm.sendCode()
            }
            .buttonStyle(OCPrimaryButtonStyle())
            .disabled(vm.isLoading)
        }
    }

    // MARK: - Code Step

    private var codeStep: some View
    {
        VStack(alignment: .leading, spacing: 12)
        {
            Text(OCStrings.localized("oc_code_subtitle"))
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)

            TextField(OCStrings.localized("oc_code_placeholder"), text: $vm.code)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .padding()
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.4)))

            if let status = vm.codeStatus
            {
                Text(status)
                    .font(.caption)
                    .foregroundColor(.green)
            }

            if let error = vm.codeError
            {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
            }

            Button(OCStrings.localized("oc_verify")) { vm.verifyCode() }
                .buttonStyle(OCPrimaryButtonStyle())
                .disabled(vm.isLoading)

            Button(OCStrings.localized("oc_resend_code")) { vm.resendCode() }
                .buttonStyle(OCSecondaryButtonStyle())
                .disabled(vm.isLoading)
        }
    }
}

// MARK: - Button Styles

internal struct OCPrimaryButtonStyle: ButtonStyle
{
    func makeBody(configuration: Configuration) -> some View
    {
        configuration.label
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.accentColor.opacity(configuration.isPressed ? 0.8 : 1))
            .foregroundColor(.white)
            .cornerRadius(8)
    }
}

internal struct OCSecondaryButtonStyle: ButtonStyle
{
    func makeBody(configuration: Configuration) -> some View
    {
        configuration.label
            .frame(maxWidth: .infinity)
            .padding()
            .foregroundColor(.accentColor)
    }
}
