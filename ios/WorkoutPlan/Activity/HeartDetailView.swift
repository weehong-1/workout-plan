import Charts
import Observation
import SwiftUI

@MainActor
@Observable
final class HeartDetailModel {
    var period: ChartPeriod = .day
    private(set) var average: [DatedValue] = []
    private(set) var resting: [DatedValue] = []
    private(set) var latest: DatedValue?
    private(set) var latestResting: DatedValue?
    private(set) var errorMessage: String?

    private let store: any HealthStore
    private let ranges: DateRanges

    init(store: any HealthStore, ranges: DateRanges = DateRanges()) {
        self.store = store
        self.ranges = ranges
    }

    var interval: DateInterval { period.interval(now: Date(), ranges: ranges) }

    var periodAverage: Double? {
        average.isEmpty ? nil : GoalMath.average(average)
    }

    func load() async {
        let interval = interval
        let bucket = period.barBucket
        let store = store
        let week = ranges.lastDaysInterval(7, endingAt: Date())
        do {
            async let averageValues = store.series(.heartRate, in: interval, bucket: bucket)
            async let restingValues = store.series(.restingHeartRate, in: interval, bucket: bucket)
            async let latestValue = store.latest(.heartRate, in: interval)
            async let latestRestingValue = store.latest(.restingHeartRate, in: week)
            let (a, r, l, lr) = try await (averageValues, restingValues, latestValue, latestRestingValue)
            average = a
            resting = r
            latest = l
            latestResting = lr
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

/// Heart detail: average heart rate over a period, with resting heart rate.
struct HeartDetailView: View {
    @State private var model: HeartDetailModel

    init(store: any HealthStore) {
        _model = State(initialValue: HeartDetailModel(store: store))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s) {
                PeriodPicker(period: $model.period)
                    .padding(.vertical, Spacing.xs)

                HStack {
                    KeyFigure(
                        title: model.period == .day ? "Latest" : "Average",
                        metric: .heart,
                        value: (model.period == .day ? model.latest?.value : model.periodAverage).map(Format.integer) ?? "–",
                        unit: "bpm"
                    )
                    KeyFigure(
                        title: "Resting",
                        metric: .heart,
                        value: model.latestResting.map { Format.integer($0.value) } ?? "–",
                        unit: "bpm"
                    )
                }
                .cardPadded()

                chart.cardPadded()

                if let message = model.errorMessage {
                    Text(message).font(AppFont.footnote).foregroundStyle(Color.appSecondaryLabel)
                }
            }
            .padding(.horizontal, Spacing.m)
            .padding(.bottom, Spacing.xl)
        }
        .background(Color.appBackground)
        .navigationTitle("Heart")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: model.period) { await model.load() }
        .refreshable { await model.load() }
    }

    @ViewBuilder
    private var chart: some View {
        if model.average.isEmpty {
            Text("No heart rate in this period")
                .font(AppFont.subheadline)
                .foregroundStyle(Color.appSecondaryLabel)
                .frame(maxWidth: .infinity, minHeight: 180)
        } else {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Chart {
                    ForEach(model.average) { point in
                        LineMark(
                            x: .value("Time", point.date),
                            y: .value("BPM", point.value),
                            series: .value("Series", "Average")
                        )
                        .interpolationMethod(.monotone)
                        .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))
                        .foregroundStyle(MetricKind.heart.graphic)
                    }
                    ForEach(model.resting) { point in
                        LineMark(
                            x: .value("Time", point.date),
                            y: .value("BPM", point.value),
                            series: .value("Series", "Resting")
                        )
                        .interpolationMethod(.monotone)
                        .lineStyle(StrokeStyle(lineWidth: 2, lineCap: .round, dash: [4, 3]))
                        .foregroundStyle(MetricKind.heart.text)
                    }
                }
                .chartXScale(domain: model.interval.start...model.interval.end)
                .chartYScale(domain: .automatic(includesZero: false))
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
                .frame(height: 180)
                .accessibilityLabel("Average heart rate\(model.resting.isEmpty ? "" : " and resting heart rate") over the period")

                if !model.resting.isEmpty {
                    Text("Solid: average · Dashed: resting")
                        .font(AppFont.caption)
                        .foregroundStyle(Color.appSecondaryLabel)
                }
            }
        }
    }
}
