import Charts
import Observation
import SwiftUI

@MainActor
@Observable
final class WorkoutsDetailModel {
    var period: ChartPeriod = .week
    private(set) var workouts: [WorkoutSummary] = []
    private(set) var errorMessage: String?

    private let store: any HealthStore
    private let ranges: DateRanges

    init(store: any HealthStore, ranges: DateRanges = DateRanges()) {
        self.store = store
        self.ranges = ranges
    }

    var interval: DateInterval { period.interval(now: Date(), ranges: ranges) }

    var minutes: [DatedValue] {
        WorkoutMath.minutes(workouts, in: interval, bucket: period.barBucket, ranges: ranges)
    }

    func load() async {
        do {
            workouts = try await store.workouts(in: interval)
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

/// Workouts detail: minutes over a period and the list of workouts.
struct WorkoutsDetailView: View {
    @State private var model: WorkoutsDetailModel

    init(store: any HealthStore) {
        _model = State(initialValue: WorkoutsDetailModel(store: store))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s) {
                PeriodPicker(period: $model.period)
                    .padding(.vertical, Spacing.xs)

                VStack(alignment: .leading, spacing: Spacing.s) {
                    HStack {
                        KeyFigure(title: "Total", metric: .exercise, value: Format.integer(WorkoutMath.totalMinutes(model.workouts)), unit: "min")
                        KeyFigure(title: "Workouts", metric: .exercise, value: "\(model.workouts.count)", unit: "")
                    }
                    Chart(model.minutes) { bar in
                        BarMark(
                            x: .value("Date", bar.date, unit: model.period.barUnit),
                            y: .value("Minutes", bar.value)
                        )
                        .foregroundStyle(MetricKind.exercise.graphic)
                        .cornerRadius(4)
                    }
                    .chartXAxis {
                        AxisMarks(values: .automatic(desiredCount: 6)) { _ in
                            AxisValueLabel(format: model.period.axisFormat)
                        }
                    }
                    .chartYAxis {
                        AxisMarks(position: .trailing) { _ in
                            AxisGridLine()
                            AxisValueLabel()
                        }
                    }
                    .frame(height: 160)
                    .accessibilityLabel("Workout minutes over the period")
                }
                .cardPadded()

                if model.workouts.isEmpty {
                    Text("No workouts in this period")
                        .font(AppFont.subheadline)
                        .foregroundStyle(Color.appSecondaryLabel)
                        .frame(maxWidth: .infinity, minHeight: 80)
                } else {
                    LazyVStack(spacing: 0) {
                        ForEach(model.workouts) { workout in
                            WorkoutRow(workout: workout)
                        }
                    }
                    .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                }

                if let message = model.errorMessage {
                    Text(message).font(AppFont.footnote).foregroundStyle(Color.appSecondaryLabel)
                }
            }
            .padding(.horizontal, Spacing.m)
            .padding(.bottom, Spacing.xl)
        }
        .background(Color.appBackground)
        .navigationTitle("Workouts")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: model.period) { await model.load() }
        .refreshable { await model.load() }
    }
}

private struct WorkoutRow: View {
    let workout: WorkoutSummary

    var body: some View {
        HStack(spacing: Spacing.s) {
            Image(systemName: workout.systemImage)
                .font(AppFont.headline)
                .foregroundStyle(MetricKind.exercise.text)
                .frame(width: 36, height: 36)
                .background(MetricKind.exercise.graphic.opacity(0.16), in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(workout.name)
                    .font(AppFont.body)
                    .foregroundStyle(Color.appLabel)
                Text(workout.start.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated).hour().minute()))
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
            Spacer(minLength: Spacing.xs)
            Text("\(Format.minutes(workout.duration)) min")
                .font(AppFont.subheadline.weight(.semibold))
                .numberDesign()
                .foregroundStyle(Color.appLabel)
        }
        .padding(.horizontal, Spacing.m)
        .padding(.vertical, Spacing.s)
        .frame(minHeight: TouchTarget.minimum)
        .accessibilityElement(children: .combine)
    }
}
