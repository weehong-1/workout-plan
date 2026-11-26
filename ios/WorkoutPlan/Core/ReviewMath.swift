import Foundation

enum ReviewKind: String, CaseIterable, Identifiable, Codable, Sendable {
    case month, year

    var id: String { rawValue }

    var title: String {
        switch self {
        case .month: "Month"
        case .year: "Year"
        }
    }
}

/// A calendar month or year to review (spec 6.4).
struct ReviewPeriod: Hashable, Sendable {
    let kind: ReviewKind
    /// First day of the month or year.
    let start: Date

    init(kind: ReviewKind, containing date: Date, calendar: Calendar = .current) {
        self.kind = kind
        let component: Calendar.Component = kind == .month ? .month : .year
        self.start = calendar.dateInterval(of: component, for: date)?.start ?? calendar.startOfDay(for: date)
    }

    func interval(calendar: Calendar = .current) -> DateInterval {
        let component: Calendar.Component = kind == .month ? .month : .year
        return calendar.dateInterval(of: component, for: start) ?? DateInterval(start: start, duration: 86_400 * 30)
    }

    /// The part of the period up to the end of today.
    func elapsed(now: Date, calendar: Calendar = .current) -> DateInterval {
        let full = interval(calendar: calendar)
        let endOfToday = calendar.dateInterval(of: .day, for: now)?.end ?? now
        return DateInterval(start: full.start, end: max(full.start, min(full.end, endOfToday)))
    }

    /// Starts of the elapsed days.
    func days(now: Date, calendar: Calendar = .current) -> [Date] {
        let range = elapsed(now: now, calendar: calendar)
        var days: [Date] = []
        var cursor = range.start
        while cursor < range.end {
            days.append(cursor)
            guard let next = calendar.date(byAdding: .day, value: 1, to: cursor) else { break }
            cursor = next
        }
        return days
    }

    var label: String {
        switch kind {
        case .month: start.formatted(.dateTime.month(.wide).year())
        case .year: start.formatted(.dateTime.year())
        }
    }

    func shifted(by count: Int, calendar: Calendar = .current) -> ReviewPeriod {
        let component: Calendar.Component = kind == .month ? .month : .year
        let date = calendar.date(byAdding: component, value: count, to: start) ?? start
        return ReviewPeriod(kind: kind, containing: date, calendar: calendar)
    }

    /// The same moment seen as the other kind (November 2026 → 2026).
    func switched(to kind: ReviewKind, now: Date, calendar: Calendar = .current) -> ReviewPeriod {
        if kind == self.kind { return self }
        if kind == .year { return ReviewPeriod(kind: .year, containing: start, calendar: calendar) }
        // Year → month: the current month for this year, otherwise December.
        let year = calendar.component(.year, from: start)
        let date = calendar.component(.year, from: now) == year
            ? now
            : calendar.date(from: DateComponents(year: year, month: 12, day: 1)) ?? start
        return ReviewPeriod(kind: .month, containing: date, calendar: calendar)
    }

    func hasNext(now: Date, calendar: Calendar = .current) -> Bool {
        shifted(by: 1, calendar: calendar).start <= now
    }
}

struct DailyActivity: Hashable, Sendable {
    let day: Date
    let steps: Double
    let moveKcal: Double
    let sleep: TimeInterval?
    let ringsClosed: Bool
}

struct ActivityReview: Sendable {
    let daily: [DailyActivity]
    /// Daily averages over the days that have data.
    let averageSteps: Double?
    let averageMoveKcal: Double?
    let averageSleep: TimeInterval?
    let ringsClosedDays: Int

    var days: Int { daily.count }
    var hasData: Bool { averageSteps != nil || averageMoveKcal != nil || averageSleep != nil }
}

struct WeightReview: Sendable {
    let entries: [WeighIn]
    let first: WeighIn
    let last: WeighIn
    /// Kilograms, last minus first.
    var change: Double { last.kilograms - first.kilograms }
    /// First and last weigh-ins of the period that have a photo, with that photo.
    let firstPhoto: (entry: WeighIn, photoID: UUID)?
    let lastPhoto: (entry: WeighIn, photoID: UUID)?
}

struct FoodReview: Sendable {
    let days: Int
    let daysWithPhotos: Int
    let photos: [FoodPhoto]
}

enum ReviewMath {
    static func activity(days: [Date], steps: [DatedValue], rings: [DayRings], nights: [Date: SleepNight], calendar: Calendar = .current) -> ActivityReview {
        var stepsByDay: [Date: Double] = [:]
        for value in steps { stepsByDay[calendar.startOfDay(for: value.date), default: 0] += value.value }
        var ringsByDay: [Date: RingValues] = [:]
        for day in rings { ringsByDay[calendar.startOfDay(for: day.day)] = day.rings }

        let daily = days.map { day -> DailyActivity in
            let rings = ringsByDay[day] ?? .empty
            let closed = rings.moveGoal > 0 && rings.moveProgress >= 1
                && rings.exerciseProgress >= 1 && rings.standProgress >= 1
            return DailyActivity(
                day: day,
                steps: stepsByDay[day] ?? 0,
                moveKcal: rings.move,
                sleep: nights[day]?.asleep,
                ringsClosed: closed
            )
        }
        func mean(_ values: [Double]) -> Double? {
            values.isEmpty ? nil : values.reduce(0, +) / Double(values.count)
        }
        return ActivityReview(
            daily: daily,
            averageSteps: mean(daily.map(\.steps).filter { $0 > 0 }),
            averageMoveKcal: mean(daily.map(\.moveKcal).filter { $0 > 0 }),
            averageSleep: mean(daily.compactMap(\.sleep)),
            ringsClosedDays: daily.filter(\.ringsClosed).count
        )
    }

    static func weight(_ entries: [WeighIn], in interval: DateInterval) -> WeightReview? {
        let inPeriod = entries.filter { interval.contains($0.day) }.sorted { $0.day < $1.day }
        guard let first = inPeriod.first, let last = inPeriod.last else { return nil }
        let withPhotos = inPeriod.filter { !$0.photoIDs.isEmpty }
        return WeightReview(
            entries: inPeriod,
            first: first,
            last: last,
            firstPhoto: withPhotos.first.map { (entry: $0, photoID: $0.photoIDs[0]) },
            lastPhoto: withPhotos.count > 1 ? withPhotos.last.map { (entry: $0, photoID: $0.photoIDs[0]) } : nil
        )
    }

    static func food(_ photos: [FoodPhoto], days: Int, calendar: Calendar = .current) -> FoodReview {
        FoodReview(
            days: days,
            daysWithPhotos: FoodMath.daysWithPhotos(photos, calendar: calendar).count,
            photos: photos.sorted { $0.takenAt < $1.takenAt }
        )
    }
}
