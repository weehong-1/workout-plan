import SwiftUI

@main
struct WorkoutPlanApp: App {
    private let healthStore = HealthStoreFactory.make()

    var body: some Scene {
        WindowGroup {
            RootView(store: healthStore)
        }
    }
}

/// First launch flow until it is finished once, then the tab bar.
struct RootView: View {
    let store: any HealthStore
    @AppStorage(StorageKey.onboardingComplete) private var onboardingComplete = false

    var body: some View {
        if onboardingComplete {
            MainTabView(store: store)
        } else {
            OnboardingFlow(store: store) {
                onboardingComplete = true
            }
        }
    }
}
