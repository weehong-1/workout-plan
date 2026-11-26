import Foundation
import Observation

@MainActor
@Observable
final class ActivityViewModel {
    enum State {
        case loading
        /// Health is not available on this device.
        case unavailable
        /// The Health permission sheet has not been shown yet.
        case needsAccess
        case failed(String)
        case loaded(ActivitySnapshot)
    }

    private(set) var state: State = .loading
    private var isLoading = false

    private let store: any HealthStore
    private let loader: ActivityLoader

    init(store: any HealthStore) {
        self.store = store
        self.loader = ActivityLoader(store: store)
    }

    func load(stepGoal: Double = GoalMath.defaultStepGoal) async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }

        guard store.isAvailable else {
            state = .unavailable
            return
        }
        if await store.needsAuthorizationRequest() {
            state = .needsAccess
            return
        }
        do {
            state = .loaded(try await loader.load(now: Date(), stepGoal: stepGoal))
        } catch {
            // Keep showing the last good data on a failed refresh.
            if case .loaded = state { return }
            state = .failed(error.localizedDescription)
        }
    }

    func requestAccess(stepGoal: Double = GoalMath.defaultStepGoal) async {
        do {
            try await store.requestAuthorization()
        } catch {
            state = .failed(error.localizedDescription)
            return
        }
        await load(stepGoal: stepGoal)
    }
}
