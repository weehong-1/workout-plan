import Foundation

/// Keys for values kept in `@AppStorage`.
enum StorageKey {
    static let onboardingComplete = "onboarding.complete"
    static let weightUnit = "settings.weightUnit"
}

/// Weight unit chosen on first launch; can be switched in the Log weigh-in sheet.
enum WeightUnit: String, CaseIterable, Identifiable, Sendable {
    case kg, lb

    var id: String { rawValue }

    var title: String {
        switch self {
        case .kg: "kg"
        case .lb: "lb"
        }
    }
}

/// The signed-in person. Sign-in is not built yet (spec 5.1), so this is fixed.
enum UserProfile {
    static let firstName = "Vernon"
    static var initial: String { String(firstName.prefix(1)) }
}
