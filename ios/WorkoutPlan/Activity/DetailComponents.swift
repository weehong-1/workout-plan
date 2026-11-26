import SwiftUI

extension View {
    /// Content padded and placed on a solid card.
    func cardPadded(_ radius: CGFloat = Radius.card) -> some View {
        padding(Spacing.m)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.appCard, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}

/// Day / Week / Month / Year segmented control.
struct PeriodPicker: View {
    @Binding var period: ChartPeriod

    var body: some View {
        Picker("Period", selection: $period) {
            ForEach(ChartPeriod.allCases) { period in
                Text(period.title).tag(period)
            }
        }
        .pickerStyle(.segmented)
    }
}

/// A key figure: coloured title over a number and unit.
struct KeyFigure: View {
    let title: String
    let metric: MetricKind
    let value: String
    let unit: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(AppFont.footnote.weight(.semibold))
                .foregroundStyle(metric.text)
            ValueWithUnit(value: value, unit: unit, size: .medium)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// Axis label format and stride for a chart period.
extension ChartPeriod {
    var axisFormat: Date.FormatStyle {
        switch self {
        case .day: .dateTime.hour()
        case .week: .dateTime.weekday(.abbreviated)
        case .month: .dateTime.day()
        case .year: .dateTime.month(.narrow)
        }
    }

    /// Bars per day for week and month, per month for year, per hour for day.
    var barBucket: Bucket {
        switch self {
        case .day: .hour
        case .week, .month: .day
        case .year: .month
        }
    }

    var barUnit: Calendar.Component {
        switch self {
        case .day: .hour
        case .week, .month: .day
        case .year: .month
        }
    }
}
