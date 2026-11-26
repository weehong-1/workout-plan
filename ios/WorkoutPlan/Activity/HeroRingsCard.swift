import SwiftUI

/// Hero card: today's rings with value / goal, then mini rings for the last 7 days.
struct HeroRingsCard: View {
    let today: RingValues
    let week: [DayRings]

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .largeTitle) private var ringSize: CGFloat = 150

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            let layout = dynamicTypeSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: Spacing.l))
                : AnyLayout(HStackLayout(alignment: .center, spacing: Spacing.l))

            layout {
                ActivityRings(
                    move: today.moveProgress,
                    exercise: today.exerciseProgress,
                    stand: today.standProgress
                )
                .frame(width: min(ringSize, 180), height: min(ringSize, 180))

                VStack(alignment: .leading, spacing: 10) {
                    RingStat(title: "Move", metric: .move, value: today.move, goal: today.moveGoal, unit: "kcal")
                    RingStat(title: "Exercise", metric: .exercise, value: today.exercise, goal: today.exerciseGoal, unit: "min")
                    RingStat(title: "Stand", metric: .stand, value: today.stand, goal: today.standGoal, unit: "hr")
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            Rectangle()
                .fill(Color.appSeparator)
                .frame(height: 0.5)
                .padding(.top, Spacing.m)
                .padding(.bottom, Spacing.s)

            WeekRingsRow(week: week)
        }
        .padding(Spacing.l)
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.hero, style: .continuous))
    }
}

/// "Move  245/400 kcal"
private struct RingStat: View {
    let title: String
    let metric: MetricKind
    let value: Double
    let goal: Double
    let unit: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(AppFont.footnote.weight(.semibold))
                .foregroundStyle(metric.text)
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text(Format.integer(value))
                    .metricFont(.medium)
                    .foregroundStyle(Color.appLabel)
                Text("/\(Format.integer(goal)) \(unit)")
                    .font(AppFont.subheadline)
                    .foregroundStyle(Color.appSecondaryLabel)
                    .numberDesign()
            }
            .lineLimit(1)
            .minimumScaleFactor(0.7)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue("\(Format.integer(value)) of \(Format.integer(goal)) \(unit)")
    }
}

/// Seven mini rings with weekday letters; today is bold.
private struct WeekRingsRow: View {
    let week: [DayRings]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(week.enumerated()), id: \.element.id) { index, day in
                let isToday = index == week.count - 1
                VStack(spacing: Spacing.xxs) {
                    Text(day.day.formatted(.dateTime.weekday(.narrow)))
                        .font(.caption2.weight(isToday ? .bold : .medium))
                        .foregroundStyle(isToday ? Color.appLabel : Color.appSecondaryLabel)
                    ActivityRings(
                        move: day.rings.moveProgress,
                        exercise: day.rings.exerciseProgress,
                        stand: day.rings.standProgress,
                        lineWidth: 3.5,
                        gap: 0.7
                    )
                    .frame(width: 32, height: 32)
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(day.day.formatted(.dateTime.weekday(.wide)))
                .accessibilityValue(
                    "Move \(Format.integer(day.rings.move)) kcal, Exercise \(Format.integer(day.rings.exercise)) min, Stand \(Format.integer(day.rings.stand)) hr"
                )
            }
        }
    }
}
