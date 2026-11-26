import Foundation

/// The insight line on the Steps detail screen.
enum StepsInsight {
    /// The bucket with the most steps, or nil when there are no steps.
    static func busiest(_ buckets: [DatedValue]) -> DatedValue? {
        guard let best = buckets.max(by: { $0.value < $1.value }), best.value > 0 else { return nil }
        return best
    }

    /// e.g. "2,765 steps to go to reach today's goal. Your busiest hour was 12 to 1 PM."
    static func message(steps: Double, goal: Double, hourly: [DatedValue], calendar: Calendar = .current) -> String {
        var parts: [String] = []
        if goal > 0 {
            let remaining = GoalMath.remaining(steps, goal: goal)
            if remaining > 0 {
                parts.append("\(Format.integer(remaining)) steps to go to reach today's goal.")
            } else {
                parts.append("Goal reached: \(Format.integer(steps - goal)) steps over today's goal.")
            }
        }
        if let busiest = busiest(hourly) {
            let end = calendar.date(byAdding: .hour, value: 1, to: busiest.date) ?? busiest.date
            parts.append("Your busiest hour was \(Format.hourRange(from: busiest.date, to: end)).")
        }
        if parts.isEmpty {
            return "No steps recorded yet today."
        }
        return parts.joined(separator: " ")
    }
}
