import Foundation
import Observation

@MainActor
@Observable
final class StepsDetailModel {
    var period: ChartPeriod = .day

    private(set) var todaySteps: Double = 0
    private(set) var activeEnergy: Double = 0
    private(set) var distanceMeters: Double = 0
    private(set) var hourlyToday: [DatedValue] = []
    private(set) var series: [DatedValue] = []
    private(set) var errorMessage: String?

    let stepGoal: Double
    private let store: any HealthStore
    private let ranges: DateRanges

    init(store: any HealthStore, stepGoal: Double, ranges: DateRanges = DateRanges()) {
        self.store = store
        self.stepGoal = stepGoal
        self.ranges = ranges
    }

    var progress: Double { GoalMath.progress(todaySteps, goal: stepGoal) }

    var insight: String {
        StepsInsight.message(steps: todaySteps, goal: stepGoal, hourly: hourlyToday, calendar: ranges.calendar)
    }

    var seriesTotal: Double { GoalMath.total(series) }

    /// Average per bar: per day for week and month, per month for year.
    /// The day view shows the total only.
    var seriesAverage: Double? {
        switch period {
        case .day: nil
        case .week, .month, .year: GoalMath.average(series)
        }
    }

    func loadToday() async {
        let store = store
        let today = ranges.day(containing: Date())
        do {
            async let steps = store.total(.steps, in: today)
            async let energy = store.total(.activeEnergy, in: today)
            async let distance = store.total(.distance, in: today)
            async let hourly = store.series(.steps, in: today, bucket: .hour)
            let (stepsValue, energyValue, distanceValue, hourlyValue) = try await (steps, energy, distance, hourly)
            todaySteps = stepsValue
            activeEnergy = energyValue
            distanceMeters = distanceValue
            hourlyToday = hourlyValue
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func loadSeries() async {
        let period = period
        let interval = period.interval(now: Date(), ranges: ranges)
        do {
            let values = try await store.series(.steps, in: interval, bucket: period.bucket)
            // Ignore a result for a period that is no longer selected.
            guard period == self.period else { return }
            series = values
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
