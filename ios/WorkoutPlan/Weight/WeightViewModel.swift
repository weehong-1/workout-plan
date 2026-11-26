import Foundation
import Observation

@MainActor
@Observable
final class WeightViewModel {
    /// Newest day first.
    private(set) var entries: [WeighIn] = []
    private(set) var isLoaded = false
    private(set) var errorMessage: String?
    var period: WeightPeriod = .month

    let store: any WeightStore
    private let ranges: DateRanges

    init(store: any WeightStore, ranges: DateRanges = DateRanges()) {
        self.store = store
        self.ranges = ranges
    }

    var latest: WeighIn? { entries.first }

    /// Kilograms; nil with fewer than two weigh-ins.
    var weeklyChange: Double? {
        WeightMath.weeklyChange(entries, calendar: ranges.calendar)
    }

    /// Kilograms, oldest first.
    var chartPoints: [DatedValue] {
        WeightMath.points(entries, period: period, now: Date(), ranges: ranges)
    }

    var chartInterval: DateInterval {
        period.chartPeriod.interval(now: Date(), ranges: ranges)
    }

    func load() async {
        do {
            entries = try await store.entries()
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoaded = true
    }
}
