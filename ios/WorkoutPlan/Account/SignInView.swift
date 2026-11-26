import SwiftUI

/// Email code sign-in, as in the web app (spec 5.1). Used on first launch
/// and from Account or Share with trainer.
struct SignInView: View {
    let account: AccountModel
    /// Shown as "Not Now" when given.
    var onSkip: (() -> Void)?
    let onSignedIn: () -> Void

    private enum Step { case email, code }

    @State private var step: Step = .email
    @State private var email = ""
    @State private var code = ""
    @State private var isWorking = false
    @State private var errorMessage: String?
    @FocusState private var focused: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                Image(systemName: "person.crop.circle.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(Color.appTint)
                    .accessibilityHidden(true)
                    .padding(.top, Spacing.xxl)

                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(step == .email ? "Sign in" : "Enter the code")
                        .font(AppFont.largeTitle)
                        .foregroundStyle(Color.appLabel)
                        .accessibilityAddTraits(.isHeader)
                    Text(step == .email
                         ? "Use the same email as the Workout Plan website. You need an account only to share a review with a trainer; your weigh-ins and food photos stay on this iPhone."
                         : "We sent a code to \(email). It expires after a few minutes.")
                        .font(AppFont.body)
                        .foregroundStyle(Color.appSecondaryLabel)
                }

                Group {
                    switch step {
                    case .email:
                        TextField("Email", text: $email)
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .submitLabel(.continue)
                            .onSubmit(sendCode)
                    case .code:
                        TextField("Code", text: $code)
                            .textContentType(.oneTimeCode)
                            .keyboardType(.numberPad)
                            .font(AppFont.title2.monospacedDigit())
                    }
                }
                .focused($focused)
                .font(AppFont.body)
                .padding(.horizontal, Spacing.m)
                .frame(minHeight: 52)
                .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.control + 4, style: .continuous))

                if let errorMessage {
                    Text(errorMessage)
                        .font(AppFont.footnote)
                        .foregroundStyle(MetricKind.heart.text)
                }

                VStack(spacing: Spacing.xs) {
                    Button(action: step == .email ? sendCode : verify) {
                        Group {
                            if isWorking {
                                ProgressView()
                            } else {
                                Text(step == .email ? "Send Code" : "Sign In")
                            }
                        }
                        .font(AppFont.headline)
                        .frame(maxWidth: .infinity, minHeight: TouchTarget.minimum)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isWorking || !canContinue)

                    if step == .code {
                        Button("Use a different email") {
                            step = .email
                            code = ""
                            errorMessage = nil
                        }
                        .frame(minHeight: TouchTarget.minimum)
                    }
                    if let onSkip {
                        Button("Not Now", action: onSkip)
                            .frame(minHeight: TouchTarget.minimum)
                    }
                }
            }
            .padding(.horizontal, Spacing.m)
            .padding(.bottom, Spacing.xl)
        }
        .background(Color.appBackground)
        .onAppear { focused = true }
    }

    private var trimmedEmail: String { email.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var trimmedCode: String { code.filter(\.isNumber) }

    private var canContinue: Bool {
        switch step {
        case .email: trimmedEmail.contains("@") && trimmedEmail.contains(".")
        case .code: trimmedCode.count >= 6
        }
    }

    private func sendCode() {
        guard canContinue else { return }
        isWorking = true
        errorMessage = nil
        Task {
            do {
                try await account.sendCode(to: trimmedEmail)
                step = .code
                focused = true
            } catch {
                errorMessage = error.localizedDescription
            }
            isWorking = false
        }
    }

    private func verify() {
        isWorking = true
        errorMessage = nil
        Task {
            do {
                try await account.verify(email: trimmedEmail, code: trimmedCode)
                onSignedIn()
            } catch {
                errorMessage = error.localizedDescription
            }
            isWorking = false
        }
    }
}
