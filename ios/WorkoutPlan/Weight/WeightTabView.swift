import SwiftUI

/// Weight tab (spec 6.2, design `Main.dc.html`): latest weight and weekly
/// change, trend chart, and the history list.
struct WeightTabView: View {
    @State private var model: WeightViewModel
    @State private var sheet: SheetMode?
    @AppStorage(StorageKey.weightUnit) private var unit: WeightUnit = .kg

    private enum SheetMode: Identifiable {
        case log
        case edit(WeighIn)

        var id: String {
            switch self {
            case .log: "log"
            case .edit(let entry): entry.id.uuidString
            }
        }

        var editing: WeighIn? {
            if case .edit(let entry) = self { return entry }
            return nil
        }
    }

    init(store: any WeightStore) {
        _model = State(initialValue: WeightViewModel(store: store))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    content
                }
                .padding(.horizontal, Spacing.m)
                .padding(.bottom, Spacing.xl)
            }
            .background(Color.appBackground)
            .navigationTitle("Weight")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Log weigh-in", systemImage: "plus") {
                        sheet = .log
                    }
                }
            }
            .accountToolbar()
            .task { await model.load() }
            .refreshable { await model.load() }
            .sheet(item: $sheet, onDismiss: {
                Task { await model.load() }
            }) { mode in
                LogWeighInSheet(editing: mode.editing, entries: model.entries, store: model.store)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if !model.isLoaded {
            ProgressView()
                .frame(maxWidth: .infinity, minHeight: 320)
        } else if let latest = model.latest {
            summaryCard(latest)
            history
        } else if let message = model.errorMessage {
            ContentUnavailableView {
                Label("Couldn't load weigh-ins", systemImage: "exclamationmark.triangle")
            } description: {
                Text(message)
            } actions: {
                Button("Try Again") { Task { await model.load() } }
                    .buttonStyle(.bordered)
            }
            .frame(maxWidth: .infinity, minHeight: 320)
        } else {
            ContentUnavailableView {
                Label("No weigh-ins yet", systemImage: "scalemass")
            } description: {
                Text("Log your weight to see your trend. A progress photo is optional.")
            } actions: {
                Button("Log Weigh-in") { sheet = .log }
                    .buttonStyle(.borderedProminent)
            }
            .frame(maxWidth: .infinity, minHeight: 320)
        }
    }

    private func summaryCard(_ latest: WeighIn) -> some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            HStack(alignment: .top, spacing: Spacing.xs) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Latest")
                        .font(AppFont.footnote.weight(.semibold))
                        .foregroundStyle(MetricKind.weight.text)
                    ValueWithUnit(value: WeightFormat.value(latest.kilograms, unit: unit), unit: unit.rawValue, size: .xl)
                    Text(WeightFormat.entryDate(latest))
                        .font(AppFont.footnote)
                        .foregroundStyle(Color.appSecondaryLabel)
                }
                .accessibilityElement(children: .combine)
                Spacer(minLength: 0)
                if let change = model.weeklyChange {
                    Text(WeightFormat.weeklyChange(change, unit: unit))
                        .font(AppFont.footnote.weight(.semibold))
                        .numberDesign()
                        .foregroundStyle(MetricKind.weight.text)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(MetricKind.weight.graphic.opacity(0.16), in: Capsule())
                }
            }

            Picker("Period", selection: $model.period) {
                ForEach(WeightPeriod.allCases) { period in
                    Text(period.title).tag(period)
                }
            }
            .pickerStyle(.segmented)

            WeightTrendChart(
                points: model.chartPoints,
                interval: model.chartInterval,
                period: model.period,
                unit: unit
            )
        }
        .padding(Spacing.m)
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }

    private var history: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            SectionHeader(title: "History")
            LazyVStack(spacing: 0) {
                ForEach(model.entries) { entry in
                    Button {
                        sheet = .edit(entry)
                    } label: {
                        HistoryRow(entry: entry, unit: unit, store: model.store)
                    }
                    .buttonStyle(.plain)
                    if entry.id != model.entries.last?.id {
                        Rectangle()
                            .fill(Color.appSeparator)
                            .frame(height: 0.5)
                            .padding(.leading, Spacing.m)
                    }
                }
            }
            .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        }
    }
}

/// "78.4 kg / Thu, 26 Nov" with the first photo and a chevron.
private struct HistoryRow: View {
    let entry: WeighIn
    let unit: WeightUnit
    let store: any WeightStore

    var body: some View {
        HStack(spacing: Spacing.s) {
            VStack(alignment: .leading, spacing: 2) {
                ValueWithUnit(value: WeightFormat.value(entry.kilograms, unit: unit), unit: unit.rawValue, size: .medium)
                Text(WeightFormat.day(entry.day))
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
            Spacer(minLength: Spacing.xs)
            if let photoID = entry.photoIDs.first {
                PhotoThumbnail(photo: .stored(photoID), store: store, maxPixelSize: 150)
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.control, style: .continuous))
            }
            Image(systemName: "chevron.right")
                .font(AppFont.footnote.weight(.semibold))
                .foregroundStyle(Color.appSecondaryLabel)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, Spacing.m)
        .padding(.vertical, Spacing.s)
        .frame(minHeight: TouchTarget.minimum)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(WeightFormat.value(entry.kilograms, unit: unit)) \(unit.rawValue), \(WeightFormat.day(entry.day))")
        .accessibilityValue(entry.photoIDs.isEmpty ? "" : "\(entry.photoIDs.count) photo\(entry.photoIDs.count == 1 ? "" : "s")")
        .accessibilityHint("Edit or delete")
    }
}

#Preview {
    WeightTabView(store: InMemoryWeightStore.sample())
        .environment(AppServices.preview)
}
