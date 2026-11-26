import Foundation

/// Goal maths shared by rings, cards and charts.
enum GoalMath {
    /// Daily step goal (spec 6.1). Health has no step goal to read; whether it
    /// becomes a setting is still open.
    static let defaultStepGoal: Double = 10_000

    /// Progress from 0 upwards; 1.0 means the goal is met. 0 when there is no goal.
    static func progress(_ value: Double, goal: Double) -> Double {
        guard goal > 0 else { return 0 }
        return max(0, value) / goal
    }

    /// What is left to reach the goal, never negative.
    static func remaining(_ value: Double, goal: Double) -> Double {
        max(0, goal - value)
    }

    /// Mean of the values; 0 for an empty list.
    static func average(_ values: [DatedValue]) -> Double {
        guard !values.isEmpty else { return 0 }
        return values.reduce(0) { $0 + $1.value } / Double(values.count)
    }

    static func total(_ values: [DatedValue]) -> Double {
        values.reduce(0) { $0 + $1.value }
    }
}
