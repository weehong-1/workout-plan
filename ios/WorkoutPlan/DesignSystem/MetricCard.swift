import SwiftUI

/// A solid card with a coloured metric title (icon + name) and free content
/// below it. Cards never get custom glass (spec 3.1).
struct MetricCard<Content: View>: View {
    let title: String
    let systemImage: String
    let metric: MetricKind
    var showsChevron = false
    var cornerRadius: CGFloat = Radius.card
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            HStack(spacing: 6) {
                Image(systemName: systemImage)
                    .imageScale(.medium)
                    .accessibilityHidden(true)
                Text(title)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if showsChevron {
                    Image(systemName: "chevron.right")
                        .font(AppFont.footnote.weight(.semibold))
                        .foregroundStyle(Color.appSecondaryLabel)
                        .accessibilityHidden(true)
                }
            }
            .font(AppFont.subheadline.weight(.semibold))
            .foregroundStyle(metric.text)

            content
        }
        .padding(Spacing.m)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

/// A number in a metric style followed by a small unit, e.g. "73 bpm".
struct ValueWithUnit: View {
    let value: String
    let unit: String
    var size: MetricSize = .large

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 3) {
            Text(value)
                .metricFont(size)
                .foregroundStyle(Color.appLabel)
            Text(unit)
                .font(AppFont.subheadline)
                .foregroundStyle(Color.appSecondaryLabel)
        }
        .lineLimit(1)
        .minimumScaleFactor(0.6)
    }
}

/// Thin capsule progress bar in a metric colour. `progress` is clamped to 0...1.
struct MetricProgressBar: View {
    let progress: Double
    let color: Color

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(color.opacity(0.22))
                Capsule()
                    .fill(color)
                    .frame(width: proxy.size.width * min(max(progress, 0), 1))
            }
        }
        .frame(height: 6)
        .accessibilityHidden(true)
    }
}

#Preview("Metric card") {
    MetricCard(title: "Steps", systemImage: "figure.walk", metric: .steps, showsChevron: true) {
        Spacer(minLength: 0)
        ValueWithUnit(value: "7,235", unit: "steps")
        MetricProgressBar(progress: 0.72, color: MetricKind.steps.graphic)
    }
    .frame(width: 180, height: 172)
    .padding()
    .background(Color.appBackground)
}
