import Charts
import SwiftUI

/// Steps detail (design `StepDetail.dc.html`): gauge, active energy and
/// distance, Day / Week / Month / Year chart and an insight line.
struct StepsDetailView: View {
    @State private var model: StepsDetailModel

    init(store: any HealthStore, stepGoal: Double) {
        _model = State(initialValue: StepsDetailModel(store: store, stepGoal: stepGoal))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.s) {
                summaryLine
                StepsGauge(steps: model.todaySteps, goal: model.stepGoal)
                    .padding(.bottom, Spacing.xxs)
                energyAndDistance
                statistics
                insightCard
                if let message = model.errorMessage {
                    Text(message)
                        .font(AppFont.footnote)
                        .foregroundStyle(Color.appSecondaryLabel)
                }
            }
            .padding(.horizontal, Spacing.m)
            .padding(.bottom, Spacing.xl)
        }
        .background(Color.appBackground)
        .navigationTitle("Steps")
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.loadToday() }
        .task(id: model.period) { await model.loadSeries() }
        .refreshable {
            await model.loadToday()
            await model.loadSeries()
        }
    }

    // "You have walked 7,235 steps today"
    private var summaryLine: some View {
        let steps = Text("\(Format.integer(model.todaySteps)) steps")
            .foregroundStyle(MetricKind.steps.text)
        return Text("You have walked \(steps) today")
            .foregroundStyle(Color.appLabel)
            .font(AppFont.title2.weight(.semibold))
            .multilineTextAlignment(.center)
            .lineLimit(3)
            .minimumScaleFactor(0.7)
            .padding(.horizontal, Spacing.xl)
            .padding(.top, Spacing.m)
    }

    private var energyAndDistance: some View {
        let distance = Format.distance(meters: model.distanceMeters)
        return HStack(spacing: 0) {
            StatColumn(title: "Active energy", metric: .move, value: Format.integer(model.activeEnergy), unit: "kcal")
            Rectangle()
                .fill(Color.appSeparator)
                .frame(width: 0.5)
            StatColumn(title: "Distance", metric: .steps, value: distance.value, unit: distance.unit)
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.vertical, 14)
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }

    private var statistics: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text("Statistics")
                .font(AppFont.headline)
                .foregroundStyle(Color.appLabel)
                .accessibilityAddTraits(.isHeader)

            Picker("Period", selection: $model.period) {
                ForEach(ChartPeriod.allCases) { period in
                    Text(period.title).tag(period)
                }
            }
            .pickerStyle(.segmented)

            HStack(alignment: .firstTextBaseline, spacing: Spacing.xs) {
                Text("Total \(Format.integer(model.seriesTotal))")
                if let average = model.seriesAverage {
                    Text("·")
                    Text("Average \(Format.integer(average))")
                }
            }
            .font(AppFont.footnote)
            .numberDesign()
            .foregroundStyle(Color.appSecondaryLabel)

            StepsPeriodChart(period: model.period, values: model.series, goal: model.stepGoal)
                .frame(height: 140)
        }
        .padding(Spacing.m)
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }

    private var insightCard: some View {
        HStack(alignment: .top, spacing: Spacing.s) {
            Image(systemName: "lightbulb.fill")
                .font(AppFont.subheadline)
                .foregroundStyle(MetricKind.steps.text)
                .frame(width: 32, height: 32)
                .background(MetricKind.steps.graphic.opacity(0.18), in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text("Insight")
                    .font(AppFont.headline)
                    .foregroundStyle(Color.appLabel)
                Text(model.insight)
                    .font(AppFont.subheadline)
                    .foregroundStyle(Color.appSecondaryLabel)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.m)
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// 270° gauge with the step count in the middle.
private struct StepsGauge: View {
    let steps: Double
    let goal: Double

    @ScaledMetric(relativeTo: .largeTitle) private var size: CGFloat = 220

    private var progress: Double { min(GoalMath.progress(steps, goal: goal), 1) }

    var body: some View {
        let diameter = min(size, 300)
        ZStack {
            GaugeArc(fraction: 1)
                .stroke(MetricKind.steps.graphic.opacity(0.22), style: StrokeStyle(lineWidth: 18, lineCap: .round))
            GaugeArc(fraction: progress)
                .stroke(MetricKind.steps.graphic, style: StrokeStyle(lineWidth: 18, lineCap: .round))
            VStack(spacing: 2) {
                Image(systemName: "figure.walk")
                    .font(AppFont.title2)
                    .foregroundStyle(MetricKind.steps.graphic)
                Text(Format.integer(steps))
                    .metricFont(.xl)
                    .foregroundStyle(Color.appLabel)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                Text("of \(Format.integer(goal)) steps")
                    .font(AppFont.subheadline)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
            .padding(.horizontal, 30)
        }
        .frame(width: diameter, height: diameter)
        .padding(.top, Spacing.xs)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Steps today")
        .accessibilityValue(
            "\(Format.integer(steps)) of \(Format.integer(goal)), \(progress.formatted(.percent.precision(.fractionLength(0)))) of the daily goal"
        )
    }
}

/// An arc that opens at the bottom: 270° from the lower left, clockwise.
private struct GaugeArc: Shape {
    var fraction: Double

    func path(in rect: CGRect) -> Path {
        let inset: CGFloat = 9
        let radius = min(rect.width, rect.height) / 2 - inset
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let start = Angle.degrees(135)
        let end = Angle.degrees(135 + 270 * min(max(fraction, 0), 1))
        var path = Path()
        guard fraction > 0 else { return path }
        path.addArc(center: center, radius: radius, startAngle: start, endAngle: end, clockwise: false)
        return path
    }
}

private struct StatColumn: View {
    let title: String
    let metric: MetricKind
    let value: String
    let unit: String

    var body: some View {
        VStack(spacing: 2) {
            Text(title)
                .font(AppFont.footnote.weight(.semibold))
                .foregroundStyle(metric.text)
            ValueWithUnit(value: value, unit: unit, size: .medium)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Spacing.xs)
        .accessibilityElement(children: .combine)
    }
}

/// Bar chart for one period. Day uses half-hour bars of fixed width.
private struct StepsPeriodChart: View {
    let period: ChartPeriod
    let values: [DatedValue]
    let goal: Double

    var body: some View {
        Chart {
            ForEach(values) { item in
                if period == .day {
                    BarMark(
                        x: .value("Time", item.date),
                        y: .value("Steps", item.value),
                        width: .fixed(4)
                    )
                    .cornerRadius(2)
                    .foregroundStyle(MetricKind.steps.graphic)
                } else {
                    BarMark(
                        x: .value("Date", item.date, unit: unit),
                        y: .value("Steps", item.value)
                    )
                    .cornerRadius(4)
                    .foregroundStyle(MetricKind.steps.graphic)
                }
            }
            if period.showsDailyGoal, goal > 0 {
                RuleMark(y: .value("Goal", goal))
                    .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                    .foregroundStyle(Color.appSecondaryLabel.opacity(0.6))
            }
        }
        .chartXAxis {
            AxisMarks(values: .stride(by: strideComponent, count: strideCount)) { _ in
                AxisGridLine()
                AxisValueLabel(format: labelFormat)
            }
        }
        .chartYAxis {
            AxisMarks(position: .trailing) { _ in
                AxisGridLine()
                AxisValueLabel()
            }
        }
        .accessibilityLabel(accessibilityLabel)
    }

    private var unit: Calendar.Component {
        switch period {
        case .day: .minute
        case .week, .month: .day
        case .year: .month
        }
    }

    private var strideComponent: Calendar.Component {
        switch period {
        case .day: .hour
        case .week, .month: .day
        case .year: .month
        }
    }

    private var strideCount: Int {
        switch period {
        case .day: 6
        case .week: 1
        case .month: 7
        case .year: 1
        }
    }

    private var labelFormat: Date.FormatStyle {
        switch period {
        case .day: .dateTime.hour()
        case .week: .dateTime.weekday(.abbreviated)
        case .month: .dateTime.day()
        case .year: .dateTime.month(.narrow)
        }
    }

    private var accessibilityLabel: String {
        switch period {
        case .day: "Steps per half hour today"
        case .week: "Steps per day for the last 7 days"
        case .month: "Steps per day for the last 30 days"
        case .year: "Steps per month for the last 12 months"
        }
    }
}

#Preview {
    NavigationStack {
        StepsDetailView(store: SampleHealthStore(), stepGoal: GoalMath.defaultStepGoal)
    }
}
