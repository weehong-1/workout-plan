import Charts
import Observation
import SwiftUI

@MainActor
@Observable
final class SleepDetailModel {
    var period: ChartPeriod = .day
    private(set) var lastNight: SleepNight?
    private(set) var lastNightSegments: [SleepSegment] = []
    private(set) var nights: [Date: SleepNight] = [:]
    private(set) var errorMessage: String?

    private let store: any HealthStore
    private let ranges: DateRanges

    init(store: any HealthStore, ranges: DateRanges = DateRanges()) {
        self.store = store
        self.ranges = ranges
    }

    var interval: DateInterval { period.interval(now: Date(), ranges: ranges) }

    var bars: [DatedValue] {
        SleepMath.asleepHours(nights, in: interval, bucket: period == .year ? .month : .day, ranges: ranges)
    }

    var nightsInPeriod: [SleepNight] {
        nights.filter { interval.contains($0.key) }.map(\.value)
    }

    func load() async {
        do {
            let segments = try await store.sleepSegments(in: ranges.lastNightWindow(now: Date()))
            lastNightSegments = SleepMath.displaySegments(segments)
            lastNight = SleepMath.summarize(segments)
            if period != .day {
                let all = try await store.sleepSegments(in: SleepMath.queryWindow(for: interval))
                nights = SleepMath.nights(all, calendar: ranges.calendar)
            }
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

/// Sleep detail: last night's stages, or nightly sleep over a period.
struct SleepDetailView: View {
    @State private var model: SleepDetailModel

    init(store: any HealthStore) {
        _model = State(initialValue: SleepDetailModel(store: store))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s) {
                PeriodPicker(period: $model.period)
                    .padding(.vertical, Spacing.xs)
                if model.period == .day {
                    lastNightSection
                } else {
                    periodSection
                }
                if let message = model.errorMessage {
                    Text(message).font(AppFont.footnote).foregroundStyle(Color.appSecondaryLabel)
                }
            }
            .padding(.horizontal, Spacing.m)
            .padding(.bottom, Spacing.xl)
        }
        .background(Color.appBackground)
        .navigationTitle("Sleep")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: model.period) { await model.load() }
        .refreshable { await model.load() }
    }

    @ViewBuilder
    private var lastNightSection: some View {
        if let night = model.lastNight {
            VStack(alignment: .leading, spacing: Spacing.s) {
                Text("Last night")
                    .font(AppFont.headline)
                HStack {
                    KeyFigure(title: "Asleep", metric: .sleep, value: Format.duration(night.asleep), unit: "")
                    KeyFigure(title: "In bed", metric: .sleep, value: Format.timeRange(night.start, night.end), unit: "")
                }
                Chart(model.lastNightSegments, id: \.start) { segment in
                    BarMark(
                        xStart: .value("Start", segment.start),
                        xEnd: .value("End", segment.end),
                        y: .value("Stage", segment.stage.title)
                    )
                    .foregroundStyle(SleepStageBar.color(for: segment.stage))
                    .cornerRadius(3)
                }
                .chartYScale(domain: ["Awake", "REM", "Core", "Deep", "Asleep"].filter { title in
                    model.lastNightSegments.contains { $0.stage.title == title }
                })
                .frame(height: 180)
                .accessibilityLabel("Sleep stages through last night")
            }
            .cardPadded()

            VStack(spacing: 0) {
                ForEach(night.stages) { stage in
                    HStack {
                        Circle().fill(SleepStageBar.color(for: stage.stage)).frame(width: 10, height: 10)
                        Text(stage.stage.title)
                        Spacer()
                        Text(Format.duration(stage.duration))
                            .numberDesign()
                            .foregroundStyle(Color.appSecondaryLabel)
                    }
                    .font(AppFont.body)
                    .frame(minHeight: TouchTarget.minimum)
                    .accessibilityElement(children: .combine)
                }
            }
            .cardPadded()
        } else {
            ContentUnavailableView("No sleep recorded last night", systemImage: "moon.zzz", description: Text("Wear your Apple Watch to bed to track sleep."))
                .frame(maxWidth: .infinity, minHeight: 280)
        }
    }

    private var periodSection: some View {
        let nights = model.nightsInPeriod
        let average = SleepMath.averageAsleep(nights)
        return VStack(alignment: .leading, spacing: Spacing.s) {
            HStack {
                KeyFigure(title: "Average asleep", metric: .sleep, value: average.map(Format.duration) ?? "–", unit: "")
                KeyFigure(title: "Nights recorded", metric: .sleep, value: "\(nights.count)", unit: "")
            }
            if model.bars.isEmpty {
                Text("No sleep recorded in this period")
                    .font(AppFont.subheadline)
                    .foregroundStyle(Color.appSecondaryLabel)
                    .frame(maxWidth: .infinity, minHeight: 160)
            } else {
                Chart(model.bars) { bar in
                    BarMark(
                        x: .value("Date", bar.date, unit: model.period.barUnit),
                        y: .value("Hours", bar.value)
                    )
                    .foregroundStyle(MetricKind.sleep.graphic)
                    .cornerRadius(4)
                }
                .chartXScale(domain: model.interval.start...model.interval.end)
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
                .accessibilityLabel("Hours asleep per \(model.period == .year ? "month" : "night")")
            }
        }
        .cardPadded()
    }
}
