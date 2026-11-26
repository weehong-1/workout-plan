import Foundation

/// Date ranges used by the Activity screens. All maths goes through the given
/// calendar so time zones and daylight saving are handled by Foundation.
struct DateRanges: Sendable {
    var calendar: Calendar = .current

    /// The calendar day that contains `date`.
    func day(containing date: Date) -> DateInterval {
        calendar.dateInterval(of: .day, for: date)
            ?? DateInterval(start: calendar.startOfDay(for: date), duration: 86_400)
    }

    /// Starts of the last `count` days, oldest first, ending with today.
    func lastDays(_ count: Int, endingAt now: Date) -> [Date] {
        let today = calendar.startOfDay(for: now)
        return (0..<count).reversed().compactMap {
            calendar.date(byAdding: .day, value: -$0, to: today)
        }
    }

    /// From the start of the first of the last `count` days to the end of today.
    func lastDaysInterval(_ count: Int, endingAt now: Date) -> DateInterval {
        let days = lastDays(count, endingAt: now)
        let start = days.first ?? calendar.startOfDay(for: now)
        return DateInterval(start: start, end: day(containing: now).end)
    }

    /// Window for "last night": 6 PM yesterday until now.
    func lastNightWindow(now: Date) -> DateInterval {
        let today = calendar.startOfDay(for: now)
        let start = calendar.date(byAdding: .hour, value: -6, to: today) ?? today
        return DateInterval(start: start, end: max(now, start))
    }

    /// One value per bucket from `interval.start` up to `interval.end`, using
    /// the value from `values` where a bucket has one and 0 elsewhere.
    func fill(_ values: [DatedValue], interval: DateInterval, bucket: Bucket) -> [DatedValue] {
        var byStart: [Date: Double] = [:]
        for value in values {
            byStart[bucketStart(for: value.date, bucket: bucket), default: 0] += value.value
        }
        var result: [DatedValue] = []
        var cursor = bucketStart(for: interval.start, bucket: bucket)
        while cursor < interval.end {
            result.append(DatedValue(date: cursor, value: byStart[cursor] ?? 0))
            guard let next = calendar.date(byAdding: bucket.dateComponents, to: cursor), next > cursor else { break }
            cursor = next
        }
        return result
    }

    /// Start of the bucket that contains `date`.
    func bucketStart(for date: Date, bucket: Bucket) -> Date {
        switch bucket {
        case .halfHour:
            let hour = calendar.dateInterval(of: .hour, for: date)?.start ?? date
            let minute = calendar.component(.minute, from: date)
            return minute >= 30 ? (calendar.date(byAdding: .minute, value: 30, to: hour) ?? hour) : hour
        case .hour:
            return calendar.dateInterval(of: .hour, for: date)?.start ?? date
        case .day:
            return calendar.startOfDay(for: date)
        case .month:
            return calendar.dateInterval(of: .month, for: date)?.start ?? date
        }
    }
}
