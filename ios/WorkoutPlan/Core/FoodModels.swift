import Foundation

/// Fixed food labels, one or none per photo (spec D12).
enum FoodLabel: String, CaseIterable, Identifiable, Codable, Sendable {
    case breakfast, lunch, dinner, snack

    var id: String { rawValue }

    var title: String {
        switch self {
        case .breakfast: "Breakfast"
        case .lunch: "Lunch"
        case .dinner: "Dinner"
        case .snack: "Snack"
        }
    }
}

/// One food photo. All photos of a day together are that day's intake.
struct FoodPhoto: Identifiable, Hashable, Sendable {
    let id: UUID
    /// When the photo was taken; orders the day's grid and decides its day.
    let takenAt: Date
    let label: FoodLabel?
    let note: String
}

enum FoodMath {
    /// Starts of the days that have at least one photo.
    static func daysWithPhotos(_ photos: [FoodPhoto], calendar: Calendar = .current) -> Set<Date> {
        Set(photos.map { calendar.startOfDay(for: $0.takenAt) })
    }

    /// The day's photos in the order they were taken, filtered by label
    /// (nil means All).
    static func photos(_ photos: [FoodPhoto], on day: Date, label: FoodLabel?, calendar: Calendar = .current) -> [FoodPhoto] {
        photos
            .filter { calendar.isDate($0.takenAt, inSameDayAs: day) }
            .filter { label == nil || $0.label == label }
            .sorted { $0.takenAt < $1.takenAt }
    }

    /// When a photo from the library has no capture date: now when adding to
    /// today, otherwise noon of the selected day.
    static func fallbackTakenAt(selectedDay: Date, now: Date, calendar: Calendar = .current) -> Date {
        if calendar.isDate(selectedDay, inSameDayAs: now) { return now }
        let start = calendar.startOfDay(for: selectedDay)
        return calendar.date(byAdding: .hour, value: 12, to: start) ?? start
    }
}
