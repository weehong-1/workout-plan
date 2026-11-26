import SwiftUI

@main
struct WorkoutPlanApp: App {
    @State private var services = AppServices.live()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(services)
        }
    }
}

/// First launch flow until it is finished once, then the tab bar.
struct RootView: View {
    @Environment(AppServices.self) private var services
    @AppStorage(StorageKey.onboardingComplete) private var onboardingComplete = false

    var body: some View {
        Group {
            if onboardingComplete {
                MainTabView()
            } else {
                OnboardingFlow(store: services.health, account: services.account) {
                    onboardingComplete = true
                }
            }
        }
        .task { await services.account.refresh() }
    }
}
