import Charts
import SwiftUI

/// Weight line over the selected period, with the last point marked.
/// `points` are in kilograms and converted to `unit` here.
struct WeightTrendChart: View {
    let points: [DatedValue]
    let interval: DateInterval
    let period: WeightPeriod
    let unit: WeightUnit

    private var shown: [DatedValue] {
        points.map { DatedValue(date: $0.date, value: WeightMath.value(kilograms: $0.value, in: unit)) }
    }

    var body: some View {
        let values = shown
        if values.isEmpty {
            Text("No weigh-ins in this period")
                .font(AppFont.subheadline)
                .foregroundStyle(Color.appSecondaryLabel)
                .frame(maxWidth: .infinity, minHeight: 150)
        } else {
            Chart {
                ForEach(values) { point in
                    LineMark(
                        x: .value("Date", point.date),
                        y: .value("Weight", point.value)
                    )
                    .interpolationMethod(.monotone)
                    .lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                    .foregroundStyle(MetricKind.weight.graphic)
                }
                if let last = values.last {
                    PointMark(
                        x: .value("Date", last.date),
                        y: .value("Weight", last.value)
                    )
                    .symbolSize(90)
                    .foregroundStyle(MetricKind.weight.graphic)
                }
            }
            .chartXScale(domain: interval.start...interval.end)
            .chartYScale(domain: WeightMath.chartDomain(values.map(\.value)))
            .chartYAxis {
                AxisMarks(position: .trailing, values: .automatic(desiredCount: 3)) { _ in
                    AxisGridLine()
                    AxisValueLabel()
                }
            }
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                    AxisValueLabel(format: xFormat)
                }
            }
            .frame(height: 150)
            .accessibilityLabel(accessibilityLabel(values))
        }
    }

    private var xFormat: Date.FormatStyle {
        switch period {
        case .week: .dateTime.weekday(.abbreviated)
        case .month: .dateTime.day().month(.abbreviated)
        case .year: .dateTime.month(.abbreviated)
        }
    }

    private func accessibilityLabel(_ values: [DatedValue]) -> String {
        guard let first = values.first, let last = values.last else { return "No weigh-ins" }
        let from = first.value.formatted(.number.precision(.fractionLength(1)))
        let to = last.value.formatted(.number.precision(.fractionLength(1)))
        return "Weight over the last \(period.title.lowercased()), from \(from) to \(to) \(unit.rawValue)"
    }
}
