import Foundation

/// Everything the Activity dashboard shows, read in one go.
struct ActivitySnapshot: Sendable {
    let date: Date
    let today: RingValues
    /// Last 7 days, oldest first, ending with today.
    let week: [DayRings]
    let steps: Double
    let stepGoal: Double
    let sleep: SleepNight?
    let heart: HeartSummary
    /// Today's workouts, newest first.
    let workouts: [WorkoutSummary]
    /// Daily steps for the last 7 days, oldest first.
    let weeklySteps: [DatedValue]
    /// Only about 30 days of history are readable (spec 6.1 banner).
    let isHistoryLimited: Bool

    var stepProgress: Double { GoalMath.progress(steps, goal: stepGoal) }
    var weeklyAverage: Double { GoalMath.average(weeklySteps) }
    var workoutMinutesToday: TimeInterval { workouts.reduce(0) { $0 + $1.duration } }

    /// Nothing at all came back from Health: no Watch data yet, or read
    /// access was turned off (HealthKit does not say which).
    var isEmpty: Bool {
        !week.contains { $0.rings.hasActivity }
            && steps == 0
            && sleep == nil
            && !heart.hasData
            && workouts.isEmpty
            && !weeklySteps.contains { $0.value > 0 }
    }
}

/// Reads an `ActivitySnapshot` from a `HealthStore`, running the queries in
/// parallel. One failing query does not fail the screen; it only fails when
/// every query fails.
struct ActivityLoader: Sendable {
    let store: any HealthStore
    var ranges = DateRanges()

    func load(now: Date, stepGoal: Double) async throws -> ActivitySnapshot {
        let store = store
        let today = ranges.day(containing: now)
        let days = ranges.lastDays(7, endingAt: now)
        let week = ranges.lastDaysInterval(7, endingAt: now)
        let night = ranges.lastNightWindow(now: now)

        async let rings = Self.attempt { try await store.activityRings(days: days) }
        async let steps = Self.attempt { try await store.total(.steps, in: today) }
        async let weeklySteps = Self.attempt { try await store.series(.steps, in: week, bucket: .day) }
        async let sleep = Self.attempt { try await store.sleepSegments(in: night) }
        async let heartHourly = Self.attempt { try await store.series(.heartRate, in: today, bucket: .hour) }
        async let heartLatest = Self.attempt { try await store.latest(.heartRate, in: today) }
        async let resting = Self.attempt { try await store.latest(.restingHeartRate, in: week) }
        async let workouts = Self.attempt { try await store.workouts(in: today) }
        async let earliest = Self.attempt { try await store.earliestSampleDate() }

        let ringsResult = await rings
        let stepsResult = await steps
        let weeklyResult = await weeklySteps
        let sleepResult = await sleep
        let heartHourlyResult = await heartHourly
        let heartLatestResult = await heartLatest
        let restingResult = await resting
        let workoutsResult = await workouts
        let earliestResult = await earliest

        let errors: [Error] = [
            ringsResult.error, stepsResult.error, weeklyResult.error, sleepResult.error,
            heartHourlyResult.error, heartLatestResult.error, restingResult.error,
            workoutsResult.error, earliestResult.error,
        ].compactMap { $0 }
        if errors.count == 9, let first = errors.first {
            throw first
        }

        let weekRings = (try? ringsResult.get()) ?? days.map { DayRings(day: $0, rings: .empty) }
        return ActivitySnapshot(
            date: now,
            today: weekRings.last?.rings ?? .empty,
            week: weekRings,
            steps: (try? stepsResult.get()) ?? 0,
            stepGoal: stepGoal,
            sleep: SleepMath.summarize((try? sleepResult.get()) ?? []),
            heart: HeartSummary(
                latest: (try? heartLatestResult.get()) ?? nil,
                resting: (try? restingResult.get()) ?? nil,
                hourly: (try? heartHourlyResult.get()) ?? []
            ),
            workouts: (try? workoutsResult.get()) ?? [],
            weeklySteps: (try? weeklyResult.get()) ?? [],
            isHistoryLimited: HistoryCheck.isLimited(
                earliestSample: (try? earliestResult.get()) ?? nil,
                now: now,
                calendar: ranges.calendar
            )
        )
    }

    private static func attempt<T>(_ operation: () async throws -> T) async -> Result<T, Error> {
        do {
            return .success(try await operation())
        } catch {
            return .failure(error)
        }
    }
}

private extension Result {
    var error: Failure? {
        if case .failure(let error) = self { return error }
        return nil
    }
}
