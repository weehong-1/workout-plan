import Foundation

extension SleepMath {
    /// Hours added to a sample's start to find the day it belongs to: sleep
    /// that starts after 6 PM counts toward the next day (the day you wake up),
    /// matching the dashboard's "last night" window.
    static let nightOffset: TimeInterval = 6 * 3_600

    /// Window to query so every night ending inside `interval` is complete.
    static func queryWindow(for interval: DateInterval) -> DateInterval {
        DateInterval(start: interval.start.addingTimeInterval(-nightOffset), end: interval.end)
    }

    /// Groups sleep samples into nights keyed by the start of the wake-up day.
    static func nights(_ segments: [SleepSegment], calendar: Calendar = .current) -> [Date: SleepNight] {
        var groups: [Date: [SleepSegment]] = [:]
        for segment in segments {
            let key = calendar.startOfDay(for: segment.start.addingTimeInterval(nightOffset))
            groups[key, default: []].append(segment)
        }
        return groups.compactMapValues { summarize($0) }
    }

    /// Hours asleep per bucket, oldest first. Day buckets hold that night;
    /// month buckets hold the average of the nights recorded in the month.
    /// Buckets without a night are left out.
    static func asleepHours(_ nights: [Date: SleepNight], in interval: DateInterval, bucket: Bucket, ranges: DateRanges) -> [DatedValue] {
        var groups: [Date: [TimeInterval]] = [:]
        for (day, night) in nights where interval.contains(day) {
            groups[ranges.bucketStart(for: day, bucket: bucket), default: []].append(night.asleep)
        }
        return groups
            .map { DatedValue(date: $0.key, value: $0.value.reduce(0, +) / Double($0.value.count) / 3_600) }
            .sorted { $0.date < $1.date }
    }

    /// Average time asleep over the nights given; nil when there are none.
    static func averageAsleep(_ nights: [SleepNight]) -> TimeInterval? {
        guard !nights.isEmpty else { return nil }
        return nights.reduce(0) { $0 + $1.asleep } / Double(nights.count)
    }
}

enum WorkoutMath {
    /// Workout minutes per bucket, with 0 for buckets without workouts.
    static func minutes(_ workouts: [WorkoutSummary], in interval: DateInterval, bucket: Bucket, ranges: DateRanges) -> [DatedValue] {
        let values = workouts.map { DatedValue(date: $0.start, value: $0.duration / 60) }
        return ranges.fill(values, interval: interval, bucket: bucket)
    }

    static func totalMinutes(_ workouts: [WorkoutSummary]) -> Double {
        workouts.reduce(0) { $0 + $1.duration } / 60
    }
}

extension GoalMath {
    /// Bounds of the step goal setting.
    static let stepGoalRange: ClosedRange<Double> = 1_000...50_000
    static let stepGoalStep: Double = 500
}
