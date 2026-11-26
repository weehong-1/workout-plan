import Foundation

/// Fixed sample data for the simulator and previews (the simulator has no
/// Watch data). Values repeat by calendar day, so the same day always shows
/// the same numbers, and nothing after "now" is ever returned.
struct SampleHealthStore: HealthStore {
    var calendar: Calendar = .current

    var isAvailable: Bool { true }

    func needsAuthorizationRequest() async -> Bool { false }

    func requestAuthorization() async throws {}

    // MARK: Sample shape

    /// Full-day step totals, cycled by day.
    private static let dailySteps: [Double] = [9_200, 11_400, 5_200, 7_800, 10_300, 5_700, 9_850, 8_120, 12_600, 6_400]

    /// Share of a day's steps per half hour, from the Steps detail design.
    private static let halfHourProfile: [Double] = [
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6, 14, 10, 22, 18, 8, 12, 30, 46, 58,
        80, 64, 40, 28, 20, 34, 52, 26, 18, 30, 44, 22, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    ]
    private static let profileTotal = halfHourProfile.reduce(0, +)

    private var ranges: DateRanges { DateRanges(calendar: calendar) }

    private func dayIndex(_ date: Date) -> Int {
        calendar.ordinality(of: .day, in: .era, for: date) ?? 0
    }

    /// Steps in the half hour that starts at `slotStart`; 0 in the future.
    private func steps(inSlotStartingAt slotStart: Date, now: Date) -> Double {
        guard slotStart < now else { return 0 }
        let daily = Self.dailySteps[dayIndex(slotStart) % Self.dailySteps.count]
        let hour = calendar.component(.hour, from: slotStart)
        let minute = calendar.component(.minute, from: slotStart)
        let slot = hour * 2 + (minute >= 30 ? 1 : 0)
        return daily * Self.halfHourProfile[slot] / Self.profileTotal
    }

    private func steps(in interval: DateInterval, now: Date) -> Double {
        var total = 0.0
        var cursor = ranges.bucketStart(for: interval.start, bucket: .halfHour)
        while cursor < interval.end, cursor < now {
            total += steps(inSlotStartingAt: cursor, now: now)
            guard let next = calendar.date(byAdding: .minute, value: 30, to: cursor) else { break }
            cursor = next
        }
        return total
    }

    private func value(_ metric: QuantityMetric, in interval: DateInterval, now: Date) -> Double {
        let stepCount = steps(in: interval, now: now)
        switch metric {
        case .steps: return stepCount
        case .activeEnergy: return stepCount * 0.034
        case .distance: return stepCount * 0.72
        case .heartRate: return 62 + min(stepCount / 25, 40)
        case .restingHeartRate: return 58
        }
    }

    // MARK: HealthStore

    func activityRings(days: [Date]) async throws -> [DayRings] {
        let now = Date()
        return days.map { day in
            let dayInterval = ranges.day(containing: day)
            let stepCount = steps(in: dayInterval, now: now)
            var standHours = 0.0
            for hour in 0..<24 {
                guard let start = calendar.date(byAdding: .hour, value: hour, to: dayInterval.start) else { continue }
                if steps(in: DateInterval(start: start, duration: 3_600), now: now) > 0 { standHours += 1 }
            }
            let rings = RingValues(
                move: stepCount * 0.034,
                moveGoal: 400,
                exercise: (stepCount / 240).rounded(),
                exerciseGoal: 30,
                stand: standHours,
                standGoal: 12
            )
            return DayRings(day: day, rings: rings)
        }
    }

    func series(_ metric: QuantityMetric, in interval: DateInterval, bucket: Bucket) async throws -> [DatedValue] {
        let now = Date()
        var result: [DatedValue] = []
        var cursor = ranges.bucketStart(for: interval.start, bucket: bucket)
        while cursor < interval.end {
            guard let next = calendar.date(byAdding: bucket.dateComponents, to: cursor), next > cursor else { break }
            if metric.isCumulative {
                result.append(DatedValue(date: cursor, value: value(metric, in: DateInterval(start: cursor, end: next), now: now)))
            } else if cursor < now {
                result.append(DatedValue(date: cursor, value: value(metric, in: DateInterval(start: cursor, end: next), now: now)))
            }
            cursor = next
        }
        return result
    }

    func total(_ metric: QuantityMetric, in interval: DateInterval) async throws -> Double {
        value(metric, in: interval, now: Date())
    }

    func latest(_ metric: QuantityMetric, in interval: DateInterval) async throws -> DatedValue? {
        let now = Date()
        switch metric {
        case .heartRate:
            let date = min(now.addingTimeInterval(-240), interval.end)
            return date >= interval.start ? DatedValue(date: date, value: 73) : nil
        case .restingHeartRate:
            let date = min(calendar.startOfDay(for: now).addingTimeInterval(7 * 3_600), interval.end)
            return date >= interval.start ? DatedValue(date: date, value: 58) : nil
        case .steps, .activeEnergy, .distance:
            return nil
        }
    }

    func sleepSegments(in interval: DateInterval) async throws -> [SleepSegment] {
        let now = Date()
        // A night of 7 hr 40 min asleep, from 11:25 PM.
        let pattern: [(SleepStage, Double)] = [
            (.core, 30), (.deep, 45), (.core, 40), (.rem, 25), (.awake, 6), (.core, 50),
            (.deep, 30), (.rem, 30), (.core, 60), (.awake, 8), (.rem, 35), (.core, 45),
            (.deep, 26), (.rem, 44), (.awake, 6),
        ]
        var cursor = calendar.startOfDay(for: interval.end).addingTimeInterval(-35 * 60)
        var segments: [SleepSegment] = []
        for (stage, minutes) in pattern {
            let end = cursor.addingTimeInterval(minutes * 60)
            if end <= now, end > interval.start, cursor < interval.end {
                segments.append(SleepSegment(stage: stage, start: cursor, end: end))
            }
            cursor = end
        }
        return segments
    }

    func workouts(in interval: DateInterval) async throws -> [WorkoutSummary] {
        let start = calendar.startOfDay(for: interval.start).addingTimeInterval(7 * 3_600 + 10 * 60)
        let duration: TimeInterval = 35 * 60
        guard interval.contains(start), start.addingTimeInterval(duration) <= Date() else { return [] }
        return [
            WorkoutSummary(
                id: UUID(),
                name: "Outdoor cycling",
                systemImage: "figure.outdoor.cycle",
                start: start,
                duration: duration
            ),
        ]
    }

    func earliestSampleDate() async throws -> Date? {
        calendar.date(byAdding: .year, value: -2, to: Date())
    }
}
