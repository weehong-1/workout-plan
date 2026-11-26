import Foundation

/// Read-only access to Health data. Everything stays on the phone.
///
/// Conformers must be safe to call from any task; the HealthKit version is an
/// actor so its queries run off the main actor.
protocol HealthStore: Sendable {
    /// False on devices without Health (for example some iPads).
    var isAvailable: Bool { get }

    /// True when the system permission sheet has not been shown yet for the
    /// types the app reads. HealthKit never says whether read access was
    /// granted, only whether it still needs to be asked.
    func needsAuthorizationRequest() async -> Bool

    /// Shows the system Health permission sheet for the read types.
    func requestAuthorization() async throws

    /// Activity rings for each given day start, in the same order. Days with
    /// no summary come back as `RingValues.empty`.
    func activityRings(days: [Date]) async throws -> [DayRings]

    /// Values per bucket inside `interval`. Cumulative metrics (steps, energy,
    /// distance) return every bucket, with 0 where there is no data; averaged
    /// metrics (heart rate) return only buckets that have data.
    func series(_ metric: QuantityMetric, in interval: DateInterval, bucket: Bucket) async throws -> [DatedValue]

    /// Sum (cumulative metrics) or average (others) over `interval`.
    func total(_ metric: QuantityMetric, in interval: DateInterval) async throws -> Double

    /// The most recent sample inside `interval`.
    func latest(_ metric: QuantityMetric, in interval: DateInterval) async throws -> DatedValue?

    /// Sleep samples overlapping `interval`, excluding "in bed".
    func sleepSegments(in interval: DateInterval) async throws -> [SleepSegment]

    /// Workouts that started inside `interval`, newest first.
    func workouts(in interval: DateInterval) async throws -> [WorkoutSummary]

    /// Start date of the oldest step sample the app can read.
    func earliestSampleDate() async throws -> Date?
}
