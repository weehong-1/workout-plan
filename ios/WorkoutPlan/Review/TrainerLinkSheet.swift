import SwiftUI
import UIKit

/// Share with trainer (spec 7, design `TrainerLink.dc.html`): trainer name,
/// period, what to include, expiry, consent, then the link.
struct TrainerLinkSheet: View {
    let service: TrainerLinkService?

    @Environment(\.dismiss) private var dismiss
    @AppStorage(StorageKey.weightUnit) private var unit: WeightUnit = .kg

    @State private var trainerName = ""
    @State private var period: ReviewPeriod
    @State private var includes = ShareIncludes()
    @State private var expires = true
    @State private var expiresOn: Date
    @State private var isCreating = false
    @State private var progress: (done: Int, total: Int)?
    @State private var createdURL: URL?
    @State private var errorMessage: String?

    init(period: ReviewPeriod, service: TrainerLinkService?) {
        self.service = service
        _period = State(initialValue: period)
        _expiresOn = State(initialValue: Self.defaultExpiry(for: period))
    }

    /// Last day of the month after the period.
    static func defaultExpiry(for period: ReviewPeriod, calendar: Calendar = .current) -> Date {
        let end = period.interval(calendar: calendar).end
        let nextMonthEnd = calendar.date(byAdding: .month, value: 1, to: end) ?? end
        return calendar.date(byAdding: .day, value: -1, to: nextMonthEnd) ?? end
    }

    var body: some View {
        NavigationStack {
            Group {
                if let createdURL {
                    created(createdURL)
                } else {
                    form
                }
            }
            .navigationTitle("Share with trainer")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if createdURL == nil {
                        Button("Cancel", systemImage: "xmark") { dismiss() }
                            .disabled(isCreating)
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if createdURL != nil {
                        Button("Done") { dismiss() }
                    }
                }
            }
        }
        .interactiveDismissDisabled(isCreating)
    }

    private var form: some View {
        Form {
            Section {
                TextField("Name, for your list", text: $trainerName)
                    .textContentType(.name)
            } header: {
                Text("Trainer")
            }

            Section {
                Picker("Period", selection: Binding(
                    get: { period.kind },
                    set: { period = period.switched(to: $0, now: Date()) }
                )) {
                    ForEach(ReviewKind.allCases) { kind in
                        Text(kind.title).tag(kind)
                    }
                }
                .pickerStyle(.segmented)
                LabeledContent("Covers", value: period.label)
            } header: {
                Text("Period")
            }

            Section {
                includeToggle("Activity and sleep", metric: .move, isOn: $includes.activity)
                includeToggle("Weight", metric: .weight, isOn: $includes.weight)
                includeToggle("Weight photos", metric: .weight, isOn: $includes.weightPhotos)
                    .disabled(!includes.weight)
                includeToggle("Food photos", metric: .food, isOn: $includes.food)
            } header: {
                Text("Include")
            } footer: {
                Text("Weight photos are the first and last progress photo of the period.")
            }

            Section {
                Toggle("Link expires", isOn: $expires)
                if expires {
                    DatePicker("Expires on", selection: $expiresOn, in: Date()..., displayedComponents: .date)
                }
            } header: {
                Text("Expiry")
            } footer: {
                Text(expires
                     ? "The link stops working after this date. You can revoke it sooner."
                     : "The link works until you revoke it in Review. Anyone it is forwarded to can open it until then.")
            }

            Section {
                Label {
                    Text("Your Apple Health summaries for this period will be copied to the server. Anyone with the link can see what you include until it expires or you revoke it.")
                        .font(AppFont.subheadline)
                } icon: {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(MetricKind.steps.text)
                }

                Button {
                    create()
                } label: {
                    HStack {
                        Spacer()
                        if isCreating {
                            ProgressView()
                            Text(progressText).padding(.leading, Spacing.xs)
                        } else {
                            Text("I agree, create link").font(AppFont.headline)
                        }
                        Spacer()
                    }
                    .frame(minHeight: TouchTarget.minimum)
                }
                .disabled(isCreating || !canCreate)
            } footer: {
                if let errorMessage {
                    Text(errorMessage).foregroundStyle(MetricKind.heart.text)
                }
            }
        }
        .onChange(of: includes.weight) { _, isOn in
            if !isOn { includes.weightPhotos = false }
        }
        .onChange(of: period) { _, newPeriod in
            expiresOn = Self.defaultExpiry(for: newPeriod)
        }
    }

    private func includeToggle(_ title: String, metric: MetricKind, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            HStack(spacing: Spacing.xs) {
                Circle().fill(metric.graphic).frame(width: 10, height: 10).accessibilityHidden(true)
                Text(title)
            }
        }
    }

    private var canCreate: Bool {
        service != nil && (includes.activity || includes.weight || includes.food)
    }

    private var progressText: String {
        guard let progress, progress.total > 0 else { return "Creating link…" }
        return "Uploading \(min(progress.done + 1, progress.total)) of \(progress.total) photos…"
    }

    private func create() {
        guard let service else { return }
        isCreating = true
        errorMessage = nil
        let request = TrainerLinkService.Request(
            trainerName: trainerName,
            period: period,
            includes: includes,
            expiresOn: expires ? expiresOn : nil,
            unit: unit
        )
        Task {
            do {
                createdURL = try await service.create(request) { done, total in
                    progress = (done, total)
                }
            } catch {
                errorMessage = "Couldn't create the link: \(error.localizedDescription)"
            }
            isCreating = false
            progress = nil
        }
    }

    private func created(_ url: URL) -> some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            Image(systemName: "link.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(Color.appTint)
                .accessibilityHidden(true)
            Text("Link ready")
                .font(AppFont.largeTitle)
            Text("Send it to your trainer. They open it in a browser; no app or account needed. You can revoke it any time in Review.")
                .font(AppFont.body)
                .foregroundStyle(Color.appSecondaryLabel)
            Text(url.absoluteString)
                .font(AppFont.footnote.monospaced())
                .textSelection(.enabled)
                .cardPadded(Radius.tile)
            ShareLink(item: url) {
                Label("Share Link", systemImage: "square.and.arrow.up")
                    .font(AppFont.headline)
                    .frame(maxWidth: .infinity, minHeight: TouchTarget.minimum)
            }
            .buttonStyle(.borderedProminent)
            Button {
                UIPasteboard.general.url = url
            } label: {
                Label("Copy Link", systemImage: "doc.on.doc")
                    .frame(maxWidth: .infinity, minHeight: TouchTarget.minimum)
            }
            .buttonStyle(.bordered)
            Spacer()
        }
        .padding(Spacing.m)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.appBackground)
    }
}
