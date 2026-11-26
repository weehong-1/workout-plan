import Foundation

/// Day / Week / Month / Year selector on detail screens.
enum ChartPeriod: String, CaseIterable, Identifiable, Sendable {
    case day, week, month, year

    var id: String { rawValue }

    var title: String {
        switch self {
        case .day: "Day"
        case .week: "Week"
        case .month: "Month"
        case .year: "Year"
        }
    }

    /// Bucket size of one bar. Day uses half hours, as in the design.
    var bucket: Bucket {
        switch self {
        case .day: .halfHour
        case .week, .month: .day
        case .year: .month
        }
    }

    /// Whether a bar is one day, so the daily goal line applies.
    var showsDailyGoal: Bool { bucket == .day }

    /// The range the chart covers, ending with the current day or month.
    func interval(now: Date, ranges: DateRanges) -> DateInterval {
        let calendar = ranges.calendar
        switch self {
        case .day:
            return ranges.day(containing: now)
        case .week:
            return ranges.lastDaysInterval(7, endingAt: now)
        case .month:
            return ranges.lastDaysInterval(30, endingAt: now)
        case .year:
            let thisMonth = calendar.dateInterval(of: .month, for: now)
                ?? DateInterval(start: calendar.startOfDay(for: now), duration: 86_400 * 30)
            let start = calendar.date(byAdding: .month, value: -11, to: thisMonth.start) ?? thisMonth.start
            return DateInterval(start: start, end: thisMonth.end)
        }
    }
}
