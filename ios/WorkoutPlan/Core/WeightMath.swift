import Foundation

/// Week / Month / Year selector on the Weight tab.
enum WeightPeriod: String, CaseIterable, Identifiable, Sendable {
    case week, month, year

    var id: String { rawValue }

    var title: String {
        switch self {
        case .week: "Week"
        case .month: "Month"
        case .year: "Year"
        }
    }

    /// Same ranges as the Activity charts: last 7 days, last 30 days, last 12 months.
    var chartPeriod: ChartPeriod {
        switch self {
        case .week: .week
        case .month: .month
        case .year: .year
        }
    }
}

enum WeightMath {
    static let kilogramsPerPound = 0.45359237
    /// Accepted weights, in kilograms.
    static let validKilograms: ClosedRange<Double> = 20...400
    /// Photos per weigh-in (design `WeighIn.dc.html`).
    static let maxPhotos = 6

    static func value(kilograms: Double, in unit: WeightUnit) -> Double {
        switch unit {
        case .kg: kilograms
        case .lb: kilograms / kilogramsPerPound
        }
    }

    static func kilograms(from value: Double, in unit: WeightUnit) -> Double {
        switch unit {
        case .kg: value
        case .lb: value * kilogramsPerPound
        }
    }

    /// Parses typed text ("78.4" or "78,4") into kilograms. Nil when empty,
    /// not a number or outside `validKilograms`.
    static func parse(_ text: String, unit: WeightUnit, locale: Locale = .current) -> Double? {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        let number = (try? Double(trimmed, format: .number.locale(locale)))
            ?? Double(trimmed.replacingOccurrences(of: ",", with: "."))
        guard let number, number.isFinite else { return nil }
        let kilograms = Self.kilograms(from: number, in: unit)
        return validKilograms.contains(kilograms) ? kilograms : nil
    }

    /// Text for the weight field, one decimal, no grouping: "78.4".
    static func inputText(kilograms: Double, unit: WeightUnit) -> String {
        value(kilograms: kilograms, in: unit).formatted(.number.precision(.fractionLength(1)).grouping(.never))
    }

    /// Change of the latest weigh-in against a week earlier, in kilograms.
    ///
    /// The baseline is the most recent entry at least 7 days before the
    /// latest one; when there is none, the oldest entry within those 7 days.
    /// Nil with fewer than two entries.
    static func weeklyChange(_ entries: [WeighIn], calendar: Calendar = .current) -> Double? {
        let sorted = entries.sorted { $0.day > $1.day }
        guard let latest = sorted.first,
              let weekAgo = calendar.date(byAdding: .day, value: -7, to: latest.day)
        else { return nil }
        let older = sorted.dropFirst()
        let baseline = older.first { $0.day <= weekAgo } ?? older.last
        guard let baseline else { return nil }
        return latest.kilograms - baseline.kilograms
    }

    /// Chart points in kilograms, oldest first: one per weigh-in for week and
    /// month, one monthly average per month for year.
    static func points(_ entries: [WeighIn], period: WeightPeriod, now: Date, ranges: DateRanges) -> [DatedValue] {
        let interval = period.chartPeriod.interval(now: now, ranges: ranges)
        let inRange = entries
            .filter { interval.contains($0.day) }
            .sorted { $0.day < $1.day }

        switch period {
        case .week, .month:
            return inRange.map { DatedValue(date: $0.day, value: $0.kilograms) }
        case .year:
            var byMonth: [Date: [Double]] = [:]
            for entry in inRange {
                byMonth[ranges.bucketStart(for: entry.day, bucket: .month), default: []].append(entry.kilograms)
            }
            return byMonth
                .map { DatedValue(date: $0.key, value: $0.value.reduce(0, +) / Double($0.value.count)) }
                .sorted { $0.date < $1.date }
        }
    }

    /// Y range for a chart with some headroom, in the display unit.
    static func chartDomain(_ values: [Double]) -> ClosedRange<Double> {
        guard let low = values.min(), let high = values.max() else { return 0...1 }
        let padding = max((high - low) * 0.15, 0.5)
        return (low - padding)...(high + padding)
    }
}

/// Weight text for display.
enum WeightFormat {
    /// "78.4"
    static func value(_ kilograms: Double, unit: WeightUnit) -> String {
        WeightMath.value(kilograms: kilograms, in: unit).formatted(.number.precision(.fractionLength(1)))
    }

    /// "−0.6 kg this week", "+0.3 lb this week", "No change this week".
    static func weeklyChange(_ kilograms: Double, unit: WeightUnit) -> String {
        let shown = WeightMath.value(kilograms: kilograms, in: unit)
        let rounded = (shown * 10).rounded() / 10
        if rounded == 0 { return "No change this week" }
        let sign = rounded < 0 ? "−" : "+"
        let amount = abs(rounded).formatted(.number.precision(.fractionLength(1)))
        return "\(sign)\(amount) \(unit.rawValue) this week"
    }

    /// "Thu, 26 Nov"
    static func day(_ date: Date) -> String {
        date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
    }

    /// "Today, 7:12 AM", "Yesterday, 7:05 AM" or "Thu, 26 Nov".
    static func entryDate(_ entry: WeighIn, calendar: Calendar = .current) -> String {
        let loggedSameDay = calendar.isDate(entry.loggedAt, inSameDayAs: entry.day)
        if calendar.isDateInToday(entry.day) {
            return loggedSameDay ? "Today, \(Format.time(entry.loggedAt))" : "Today"
        }
        if calendar.isDateInYesterday(entry.day) {
            return loggedSameDay ? "Yesterday, \(Format.time(entry.loggedAt))" : "Yesterday"
        }
        return day(entry.day)
    }
}
