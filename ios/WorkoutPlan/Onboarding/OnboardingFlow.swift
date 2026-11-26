import SwiftUI

/// First launch (spec 5): Sign in → Why Health → system Health prompt → Units.
/// Sign in can be skipped, and is left out when the build has no server set up.
struct OnboardingFlow: View {
    let store: any HealthStore
    let account: AccountModel
    let onFinish: () -> Void

    private enum Step {
        case signIn, whyHealth, units
    }

    @State private var step: Step
    @State private var isRequesting = false

    init(store: any HealthStore, account: AccountModel, onFinish: @escaping () -> Void) {
        self.store = store
        self.account = account
        self.onFinish = onFinish
        _step = State(initialValue: account.isAvailable ? .signIn : .whyHealth)
    }

    var body: some View {
        Group {
            switch step {
            case .signIn:
                SignInView(
                    account: account,
                    onSkip: { step = .whyHealth },
                    onSignedIn: { step = .whyHealth }
                )
            case .whyHealth:
                WhyHealthView(
                    isRequesting: isRequesting,
                    onContinue: requestHealthAccess,
                    onSkip: { step = .units }
                )
            case .units:
                UnitPickerView(onDone: onFinish)
            }
        }
        .animation(.default, value: step)
    }

    private func requestHealthAccess() {
        isRequesting = true
        Task {
            // A refusal or an error is not a dead end: the Activity tab shows
            // a Connect button or the Settings path afterwards.
            try? await store.requestAuthorization()
            isRequesting = false
            step = .units
        }
    }
}
