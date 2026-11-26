import CoreGraphics

/// Spacing on a 4 pt grid (spec 3.1).
enum Spacing {
    static let xxs: CGFloat = 4
    static let xs: CGFloat = 8
    static let s: CGFloat = 12
    /// Screen margin and card padding.
    static let m: CGFloat = 16
    /// Hero card padding.
    static let l: CGFloat = 20
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
}

/// Corner radii (spec 3.1).
enum Radius {
    static let control: CGFloat = 10
    static let tile: CGFloat = 16
    static let card: CGFloat = 24
    static let hero: CGFloat = 28
}

/// Minimum touch target.
enum TouchTarget {
    static let minimum: CGFloat = 44
}
