import Foundation

// Plain value types for Health data. No HealthKit or SwiftUI here, so the
// logic that uses them can be read and tested on its own.

/// A value at the start of a time bucket (an hour, a day, a month...).
struct DatedValue: Identifiable, Hashable, Sendable {
    let date: Date
    let value: Double
    var id: Date { date }
}

/// The three Activity rings for one day.
struct RingValues: Hashable, Sendable {
    var move: Double          // kcal
    var moveGoal: Double      // kcal
    var exercise: Double      // minutes
    var exerciseGoal: Double  // minutes
    var stand: Double         // hours
    var standGoal: Double     // hours

    static let empty = RingValues(move: 0, moveGoal: 0, exercise: 0, exerciseGoal: 0, stand: 0, standGoal: 0)

    var moveProgress: Double { GoalMath.progress(move, goal: moveGoal) }
    var exerciseProgress: Double { GoalMath.progress(exercise, goal: exerciseGoal) }
    var standProgress: Double { GoalMath.progress(stand, goal: standGoal) }

    var hasActivity: Bool { move > 0 || exercise > 0 || stand > 0 }
}

/// Rings for a calendar day (`day` is the start of that day).
struct DayRings: Identifiable, Hashable, Sendable {
    let day: Date
    let rings: RingValues
    var id: Date { day }
}

enum SleepStage: String, CaseIterable, Hashable, Sendable {
    case awake, rem, core, deep
    /// Asleep, but the source did not record a stage.
    case unspecified

    var isAsleep: Bool { self != .awake }

    var title: String {
        switch self {
        case .awake: "Awake"
        case .rem: "REM"
        case .core: "Core"
        case .deep: "Deep"
        case .unspecified: "Asleep"
        }
    }
}

/// One sleep sample from Health.
struct SleepSegment: Hashable, Sendable {
    let stage: SleepStage
    let start: Date
    let end: Date
    var duration: TimeInterval { max(0, end.timeIntervalSince(start)) }
}

struct StageTotal: Identifiable, Hashable, Sendable {
    let stage: SleepStage
    let duration: TimeInterval
    var id: SleepStage { stage }
}

/// A summarised night of sleep.
struct SleepNight: Hashable, Sendable {
    let start: Date
    let end: Date
    /// Time asleep (every stage except awake).
    let asleep: TimeInterval
    /// Totals per stage in display order: awake, REM, core, deep, unspecified.
    let stages: [StageTotal]
}

struct HeartSummary: Hashable, Sendable {
    let latest: DatedValue?
    let resting: DatedValue?
    /// Hourly average heart rate today; only hours with data.
    let hourly: [DatedValue]

    static let empty = HeartSummary(latest: nil, resting: nil, hourly: [])

    var hasData: Bool { latest != nil || resting != nil || !hourly.isEmpty }
}

struct WorkoutSummary: Identifiable, Hashable, Sendable {
    let id: UUID
    let name: String
    let systemImage: String
    let start: Date
    let duration: TimeInterval
}

/// Quantity types the app reads.
enum QuantityMetric: Sendable {
    case steps
    case activeEnergy
    case distance
    case heartRate
    case restingHeartRate

    /// Summed per bucket (steps) rather than averaged (heart rate).
    var isCumulative: Bool {
        switch self {
        case .steps, .activeEnergy, .distance: true
        case .heartRate, .restingHeartRate: false
        }
    }
}

/// Size of one chart bucket.
enum Bucket: Sendable {
    case halfHour, hour, day, month

    var dateComponents: DateComponents {
        switch self {
        case .halfHour: DateComponents(minute: 30)
        case .hour: DateComponents(hour: 1)
        case .day: DateComponents(day: 1)
        case .month: DateComponents(month: 1)
        }
    }
}
