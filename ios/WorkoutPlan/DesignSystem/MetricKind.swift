import SwiftUI

/// One colour per metric everywhere (spec 3.1).
/// `graphic` is for rings, bars and lines; `text` is the text-safe colour for
/// small labels and numbers.
enum MetricKind: CaseIterable, Sendable {
    case move, exercise, stand, steps, sleep, heart, weight, food

    var graphic: Color {
        switch self {
        case .move: Color(hex: 0xFA114F)
        case .exercise: Color(hex: 0x7ED321)
        case .stand: Color(hex: 0x00C7D9)
        case .steps: Color(hex: 0xFF9500)
        case .sleep: Color(hex: 0x8E5CF7)
        case .heart: Color(hex: 0xFF3B30)
        case .weight: Color(hex: 0x0A84FF)
        case .food: Color(hex: 0xFFB000)
        }
    }

    var text: Color {
        switch self {
        case .move: Color(light: 0xC8003C, dark: 0xFF375F)
        case .exercise: Color(light: 0x3B7A00, dark: 0x9BE34A)
        case .stand: Color(light: 0x00798A, dark: 0x3DDCEA)
        case .steps: Color(light: 0xB25E00, dark: 0xFF9F0A)
        case .sleep: Color(light: 0x6A3BD1, dark: 0xA48BFF)
        case .heart: Color(light: 0xC9201A, dark: 0xFF453A)
        case .weight: Color(light: 0x0062CC, dark: 0x409CFF)
        case .food: Color(light: 0x946200, dark: 0xFFC23D)
        }
    }
}

/// Sleep stage colours (spec 3.1), used by the sleep stage bar.
enum SleepStageColor {
    static let awake = Color(hex: 0xFF7A59)
    static let rem = Color(hex: 0x5AC8FA)
    static let core = Color(hex: 0x8E5CF7)
    static let deep = Color(hex: 0x4B2FA8)
}
