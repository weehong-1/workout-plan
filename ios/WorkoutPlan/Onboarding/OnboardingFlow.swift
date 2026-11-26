import SwiftUI

/// First launch (spec 5): Why Health → system Health prompt → Units.
/// Sign in (spec 5.1) is not part of phase 1.
struct OnboardingFlow: View {
    let store: any HealthStore
    let onFinish: () -> Void

    private enum Step {
        case whyHealth, units
    }

    @State private var step: Step = .whyHealth
    @State private var isRequesting = false

    var body: some View {
        Group {
            switch step {
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
