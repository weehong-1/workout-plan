import Charts
import SwiftUI

/// "Steps this week": 7 daily bars, a dashed goal line and the average.
struct WeeklyStepsCard: View {
    let days: [DatedValue]
    let goal: Double

    private var average: Double { GoalMath.average(days) }
    private var lastDay: Date? { days.last?.date }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text("Steps this week")
                    .font(AppFont.headline)
                    .foregroundStyle(Color.appLabel)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: Spacing.xs)
                HStack(alignment: .firstTextBaseline, spacing: Spacing.xxs) {
                    Text("Average")
                        .font(AppFont.footnote)
                        .foregroundStyle(Color.appSecondaryLabel)
                    Text(Format.integer(average))
                        .font(AppFont.subheadline.bold())
                        .numberDesign()
                        .foregroundStyle(Color.appLabel)
                }
            }

            Chart {
                ForEach(days) { day in
                    BarMark(
                        x: .value("Day", day.date, unit: .day),
                        y: .value("Steps", day.value)
                    )
                    .cornerRadius(6)
                    .foregroundStyle(
                        MetricKind.steps.graphic.opacity(day.date == lastDay ? 1 : 0.42)
                    )
                }
                if goal > 0 {
                    RuleMark(y: .value("Goal", goal))
                        .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                        .foregroundStyle(Color.appSecondaryLabel.opacity(0.6))
                        .annotation(position: .top, alignment: .trailing) {
                            Text("Goal")
                                .font(.caption2)
                                .foregroundStyle(Color.appSecondaryLabel)
                        }
                }
            }
            .chartYAxis(.hidden)
            .chartXAxis {
                AxisMarks(values: .stride(by: .day)) { _ in
                    AxisValueLabel(format: .dateTime.weekday(.narrow), centered: true)
                }
            }
            .frame(height: 130)
            .accessibilityLabel("Daily steps for the last 7 days against a \(Format.integer(goal)) step goal")
        }
        .padding(Spacing.m)
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }
}
