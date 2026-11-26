import Foundation

enum HealthStoreFactory {
    /// Sample data on the simulator in debug builds (no Watch data there);
    /// real HealthKit everywhere else.
    static func make() -> any HealthStore {
        #if DEBUG && targetEnvironment(simulator)
        return SampleHealthStore()
        #else
        return HealthKitStore()
        #endif
    }
}
