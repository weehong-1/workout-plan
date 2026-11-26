import SwiftUI

/// Type scale (spec 3.1). Words use the system font; numbers use the rounded
/// design. Every style follows Dynamic Type.
enum AppFont {
    static let largeTitle = Font.largeTitle.bold()      // 34 bold
    static let title2 = Font.title2.bold()              // 22 bold
    static let headline = Font.headline                 // 17 semibold
    static let body = Font.body                         // 17
    static let subheadline = Font.subheadline           // 15
    static let footnote = Font.footnote                 // 13
    static let caption = Font.caption                   // 12
}

/// Size of a large metric number.
enum MetricSize {
    /// Metric XL, 44 bold.
    case xl
    /// Metric L, 30 bold.
    case large
    /// Ring values in the hero card, 24 bold (from the Activity design).
    case medium

    fileprivate var baseSize: CGFloat {
        switch self {
        case .xl: 44
        case .large: 30
        case .medium: 24
        }
    }
}

/// Rounded, bold number that scales with Dynamic Type.
private struct MetricNumberFont: ViewModifier {
    @ScaledMetric private var size: CGFloat

    init(_ metricSize: MetricSize) {
        _size = ScaledMetric(wrappedValue: metricSize.baseSize, relativeTo: .largeTitle)
    }

    func body(content: Content) -> some View {
        content
            .font(.system(size: size, weight: .bold, design: .rounded))
            .monospacedDigit()
    }
}

extension View {
    /// Applies a Metric XL / L / M number style.
    func metricFont(_ size: MetricSize) -> some View {
        modifier(MetricNumberFont(size))
    }

    /// Rounded design for numbers set in a regular text style.
    func numberDesign() -> some View {
        fontDesign(.rounded).monospacedDigit()
    }
}
