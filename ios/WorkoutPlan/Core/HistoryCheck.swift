import Foundation

/// Detects when only about 30 days of Health history can be read (spec 5.3
/// and 6.1). iOS 27 reportedly lets people share "Past 30 Days" only; the app
/// cannot ask which option was chosen, so it looks at the earliest sample it
/// can read.
enum HistoryCheck {
    /// Days of history below which the banner shows. A little above 30 so a
    /// sample just inside the 30-day window still counts as limited.
    static let limitedThresholdDays = 32

    /// True when the earliest readable sample is newer than the threshold.
    /// False when there is no data at all (that is the "no data" state).
    static func isLimited(earliestSample: Date?, now: Date, calendar: Calendar = .current) -> Bool {
        guard let earliestSample,
              let threshold = calendar.date(byAdding: .day, value: -limitedThresholdDays, to: now)
        else { return false }
        return earliestSample > threshold
    }
}
