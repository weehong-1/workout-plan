import Charts
import SwiftUI

/// Steps: value, "of <goal> steps" and a progress bar. Opens the Steps detail.
struct StepsHighlightCard: View {
    let steps: Double
    let goal: Double

    var body: some View {
        MetricCard(title: "Steps", systemImage: "figure.walk", metric: .steps, showsChevron: true) {
            Spacer(minLength: Spacing.xs)
            Text(Format.integer(steps))
                .metricFont(.large)
                .foregroundStyle(Color.appLabel)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text("of \(Format.integer(goal)) steps")
                .font(AppFont.footnote)
                .foregroundStyle(Color.appSecondaryLabel)
            MetricProgressBar(progress: GoalMath.progress(steps, goal: goal), color: MetricKind.steps.graphic)
                .padding(.top, 6)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Steps")
        .accessibilityValue("\(Format.integer(steps)) of \(Format.integer(goal))")
        .accessibilityHint("Opens step details")
        .accessibilityAddTraits(.isButton)
    }
}

/// Sleep: last night's total, start–end time and the stage bar.
struct SleepHighlightCard: View {
    let night: SleepNight?

    var body: some View {
        MetricCard(title: "Sleep", systemImage: "moon.fill", metric: .sleep, showsChevron: true) {
            Spacer(minLength: Spacing.xs)
            if let night {
                let parts = Format.hoursMinutes(night.asleep)
                HStack(alignment: .firstTextBaseline, spacing: 3) {
                    Text("\(parts.hours)").metricFont(.large).foregroundStyle(Color.appLabel)
                    Text("hr").font(AppFont.subheadline).foregroundStyle(Color.appSecondaryLabel)
                    Text("\(parts.minutes)").metricFont(.large).foregroundStyle(Color.appLabel)
                    Text("min").font(AppFont.subheadline).foregroundStyle(Color.appSecondaryLabel)
                }
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                Text(Format.timeRange(night.start, night.end))
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                SleepStageBar(stages: night.stages)
                    .padding(.top, 6)
            } else {
                Text("No sleep recorded last night")
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Sleep")
        .accessibilityValue(accessibilityValue)
        .accessibilityHint("Opens details")
        .accessibilityAddTraits(.isButton)
    }

    private var accessibilityValue: String {
        guard let night else { return "No sleep recorded last night" }
        let stages = night.stages
            .map { "\($0.stage.title) \(Format.duration($0.duration))" }
            .joined(separator: ", ")
        return "\(Format.duration(night.asleep)) asleep, \(Format.timeRange(night.start, night.end)). \(stages)"
    }
}

/// Horizontal bar with one segment per sleep stage, sized by its share.
struct SleepStageBar: View {
    let stages: [StageTotal]
    private let spacing: CGFloat = 2

    var body: some View {
        GeometryReader { proxy in
            let total = stages.reduce(0) { $0 + $1.duration }
            let available = max(0, proxy.size.width - spacing * CGFloat(max(stages.count - 1, 0)))
            HStack(spacing: spacing) {
                ForEach(stages) { item in
                    Capsule()
                        .fill(Self.color(for: item.stage))
                        .frame(width: total > 0 ? available * item.duration / total : 0)
                }
            }
        }
        .frame(height: 6)
        .accessibilityHidden(true)
    }

    static func color(for stage: SleepStage) -> Color {
        switch stage {
        case .awake: SleepStageColor.awake
        case .rem: SleepStageColor.rem
        case .core: SleepStageColor.core
        case .deep: SleepStageColor.deep
        case .unspecified: MetricKind.sleep.graphic
        }
    }
}

/// Heart: small line chart of today, latest bpm and resting bpm.
struct HeartHighlightCard: View {
    let heart: HeartSummary

    var body: some View {
        MetricCard(title: "Heart", systemImage: "heart.fill", metric: .heart, showsChevron: true) {
            if heart.hourly.count > 1 {
                Chart(heart.hourly) { point in
                    LineMark(
                        x: .value("Time", point.date),
                        y: .value("BPM", point.value)
                    )
                    .interpolationMethod(.catmullRom)
                    .lineStyle(StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                    .foregroundStyle(MetricKind.heart.graphic)
                }
                .chartXAxis(.hidden)
                .chartYAxis(.hidden)
                .chartYScale(domain: .automatic(includesZero: false))
                .frame(height: 34)
                .padding(.top, 6)
                .accessibilityHidden(true)
            }
            Spacer(minLength: Spacing.xs)
            if let latest = heart.latest {
                ValueWithUnit(value: Format.integer(latest.value), unit: "bpm")
            } else {
                Text("No heart rate today")
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
            if let resting = heart.resting {
                Text("Resting \(Format.integer(resting.value)) bpm")
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Heart")
        .accessibilityValue(accessibilityValue)
        .accessibilityHint("Opens details")
        .accessibilityAddTraits(.isButton)
    }

    private var accessibilityValue: String {
        var parts: [String] = []
        if let latest = heart.latest { parts.append("Latest \(Format.integer(latest.value)) beats per minute") }
        if let resting = heart.resting { parts.append("resting \(Format.integer(resting.value))") }
        return parts.isEmpty ? "No heart rate today" : parts.joined(separator: ", ")
    }
}

/// Workouts: today's total minutes and the latest workout.
struct WorkoutsHighlightCard: View {
    let workouts: [WorkoutSummary]

    private var totalMinutes: TimeInterval { workouts.reduce(0) { $0 + $1.duration } }

    var body: some View {
        MetricCard(title: "Workouts", systemImage: "figure.run", metric: .exercise, showsChevron: true) {
            Spacer(minLength: Spacing.xs)
            ValueWithUnit(value: Format.minutes(totalMinutes), unit: "min")
            if let latest = workouts.first {
                Text("\(latest.name) · \(Format.time(latest.start))")
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
                    .lineLimit(2)
            } else {
                Text("No workouts today")
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
            Text("From Apple Health")
                .font(AppFont.caption)
                .foregroundStyle(Color.appSecondaryLabel)
                .padding(.top, 6)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Workouts")
        .accessibilityValue(accessibilityValue)
        .accessibilityHint("Opens details")
        .accessibilityAddTraits(.isButton)
    }

    private var accessibilityValue: String {
        guard let latest = workouts.first else { return "No workouts today" }
        return "\(Format.minutes(totalMinutes)) minutes today. Latest: \(latest.name) at \(Format.time(latest.start))"
    }
}
