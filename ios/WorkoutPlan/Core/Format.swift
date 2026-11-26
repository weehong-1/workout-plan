import Foundation

/// Text formatting for numbers, times and durations. Locale aware.
enum Format {
    /// "7,235"
    static func integer(_ value: Double) -> String {
        Int(value.rounded()).formatted(.number)
    }

    /// Hours and minutes parts of a duration, e.g. (7, 40).
    static func hoursMinutes(_ seconds: TimeInterval) -> (hours: Int, minutes: Int) {
        let totalMinutes = Int((max(0, seconds) / 60).rounded())
        return (totalMinutes / 60, totalMinutes % 60)
    }

    /// "7 hr 40 min", for accessibility labels and plain text.
    static func duration(_ seconds: TimeInterval) -> String {
        let parts = hoursMinutes(seconds)
        if parts.hours == 0 { return "\(parts.minutes) min" }
        return "\(parts.hours) hr \(parts.minutes) min"
    }

    /// Whole minutes, e.g. "35".
    static func minutes(_ seconds: TimeInterval) -> String {
        integer(max(0, seconds) / 60)
    }

    /// "7:10 AM"
    static func time(_ date: Date) -> String {
        date.formatted(date: .omitted, time: .shortened)
    }

    /// "11:25 PM – 7:05 AM"
    static func timeRange(_ start: Date, _ end: Date) -> String {
        "\(time(start)) – \(time(end))"
    }

    /// "12 to 1 PM" style range for an hour bucket.
    static func hourRange(from start: Date, to end: Date) -> String {
        "\(start.formatted(.dateTime.hour())) to \(end.formatted(.dateTime.hour()))"
    }

    /// "THURSDAY, 26 NOVEMBER" (upper-cased by the view).
    static func longDate(_ date: Date) -> String {
        date.formatted(.dateTime.weekday(.wide).day().month(.wide))
    }

    /// Distance in the locale's road unit, e.g. "5.2 km" or "3.2 mi".
    static func distance(meters: Double) -> (value: String, unit: String) {
        let usesMiles = Locale.current.measurementSystem == .us
        let unit: UnitLength = usesMiles ? .miles : .kilometers
        let converted = Measurement(value: max(0, meters), unit: UnitLength.meters).converted(to: unit).value
        let value = converted.formatted(.number.precision(.fractionLength(1)))
        return (value, usesMiles ? "mi" : "km")
    }
}
