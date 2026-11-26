import SwiftUI
import UIKit

// Design tokens: surfaces and tint (spec 3.1). Asset-free: each colour is a
// dynamic UIColor that resolves light or dark from the trait collection.

extension UIColor {
    /// Creates an opaque sRGB colour from a 0xRRGGBB value.
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}

extension Color {
    /// A colour with one value for light mode and one for dark mode.
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        })
    }

    /// A colour that is the same in light and dark mode.
    init(hex: UInt32) {
        self.init(uiColor: UIColor(hex: hex))
    }

    static let appBackground = Color(light: 0xF2F2F7, dark: 0x000000)
    static let appCard = Color(light: 0xFFFFFF, dark: 0x1C1C1E)
    static let appFill = Color(light: 0xEEEEF0, dark: 0x2C2C2E)
    static let appSeparator = Color(light: 0xE5E5EA, dark: 0x38383A)
    static let appLabel = Color(light: 0x1C1C1E, dark: 0xFFFFFF)
    static let appSecondaryLabel = Color(light: 0x6C6C70, dark: 0x98989F)
    static let appTint = Color(light: 0x007AFF, dark: 0x0A84FF)
}
