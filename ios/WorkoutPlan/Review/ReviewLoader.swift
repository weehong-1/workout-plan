import Foundation

/// Everything the Review tab and a trainer link show for one period.
struct ReviewData: Sendable {
    let period: ReviewPeriod
    let days: [Date]
    /// Nil when Apple Health could not be read at all.
    let activity: ActivityReview?
    let weight: WeightReview?
    let food: FoodReview
}

/// Reads a period's review from Health and the on-phone stores.
struct ReviewLoader: Sendable {
    let health: any HealthStore
    let weight: any WeightStore
    let food: any FoodStore
    var calendar: Calendar = .current

    func load(_ period: ReviewPeriod, now: Date) async throws -> ReviewData {
        let health = health
        let calendar = calendar
        let days = period.days(now: now, calendar: calendar)
        let elapsed = period.elapsed(now: now, calendar: calendar)

        async let activity = Self.activity(health: health, days: days, interval: elapsed, calendar: calendar)
        async let entries = weight.entries()
        async let photos = food.photos(in: elapsed)

        let review = await activity
        let weightReview = ReviewMath.weight(try await entries, in: period.interval(calendar: calendar))
        let foodReview = ReviewMath.food(try await photos, days: days.count, calendar: calendar)
        return ReviewData(period: period, days: days, activity: review, weight: weightReview, food: foodReview)
    }

    private static func activity(health: any HealthStore, days: [Date], interval: DateInterval, calendar: Calendar) async -> ActivityReview? {
        guard health.isAvailable, !days.isEmpty else { return nil }
        do {
            async let steps = health.series(.steps, in: interval, bucket: .day)
            async let rings = health.activityRings(days: days)
            async let sleep = health.sleepSegments(in: SleepMath.queryWindow(for: interval))
            let (stepValues, ringValues, segments) = try await (steps, rings, sleep)
            return ReviewMath.activity(
                days: days,
                steps: stepValues,
                rings: ringValues,
                nights: SleepMath.nights(segments, calendar: calendar),
                calendar: calendar
            )
        } catch {
            return nil
        }
    }
}
