import SwiftUI

@main
struct WorkoutPlanApp: App {
    private let healthStore = HealthStoreFactory.make()
    private let weightStore = WeightStoreFactory.make()

    var body: some Scene {
        WindowGroup {
            RootView(store: healthStore, weightStore: weightStore)
        }
    }
}

/// First launch flow until it is finished once, then the tab bar.
struct RootView: View {
    let store: any HealthStore
    let weightStore: any WeightStore
    @AppStorage(StorageKey.onboardingComplete) private var onboardingComplete = false

    var body: some View {
        if onboardingComplete {
            MainTabView(store: store, weightStore: weightStore)
        } else {
            OnboardingFlow(store: store) {
                onboardingComplete = true
            }
        }
    }
}
