import SwiftUI

/// Review tab (spec 6.4, design `Review.dc.html`): a month or a year of
/// activity, weight and food, sharing with a trainer, and active links.
struct ReviewTabView: View {
    @Environment(AppServices.self) private var services
    @AppStorage(StorageKey.weightUnit) private var unit: WeightUnit = .kg
    @State private var model: ReviewModel?
    @State private var showsShare = false
    @State private var showsSignIn = false
    @State private var revoking: ShareLinkSummary?

    var body: some View {
        NavigationStack {
            ScrollView {
                if let model {
                    content(model)
                        .padding(.horizontal, Spacing.m)
                        .padding(.bottom, Spacing.xl)
                }
            }
            .background(Color.appBackground)
            .navigationTitle("Review")
            .navigationBarTitleDisplayMode(.large)
            .accountToolbar()
            .task {
                if model == nil { model = ReviewModel(services: services) }
                await model?.load()
                await model?.loadLinks()
            }
            .refreshable {
                await model?.load()
                await model?.loadLinks()
            }
            .sheet(isPresented: $showsShare, onDismiss: {
                Task { await model?.loadLinks() }
            }) {
                if let model {
                    TrainerLinkSheet(period: model.period, service: model.linkService)
                }
            }
            .navigationDestination(isPresented: $showsSignIn) {
                SignInView(account: services.account) {
                    showsSignIn = false
                    showsShare = true
                }
                .navigationBarTitleDisplayMode(.inline)
            }
            .confirmationDialog(
                "Revoke this link?",
                isPresented: Binding(get: { revoking != nil }, set: { if !$0 { revoking = nil } }),
                titleVisibility: .visible,
                presenting: revoking
            ) { link in
                Button("Revoke Link", role: .destructive) {
                    Task { await model?.revoke(link) }
                }
            } message: { _ in
                Text("The link stops working at once and its photos are deleted from the server.")
            }
        }
    }

    @ViewBuilder
    private func content(_ model: ReviewModel) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Picker("Period", selection: Binding(
                get: { model.period.kind },
                set: { kind in Task { await model.setKind(kind) } }
            )) {
                ForEach(ReviewKind.allCases) { kind in
                    Text(kind.title).tag(kind)
                }
            }
            .pickerStyle(.segmented)

            HStack {
                Button {
                    Task { await model.move(by: -1) }
                } label: {
                    Image(systemName: "chevron.left")
                        .frame(width: TouchTarget.minimum, height: TouchTarget.minimum)
                }
                .accessibilityLabel("Previous \(model.period.kind.title.lowercased())")
                Spacer()
                Text(model.period.label)
                    .font(AppFont.headline)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button {
                    Task { await model.move(by: 1) }
                } label: {
                    Image(systemName: "chevron.right")
                        .frame(width: TouchTarget.minimum, height: TouchTarget.minimum)
                }
                .disabled(!model.hasNext)
                .accessibilityLabel("Next \(model.period.kind.title.lowercased())")
            }

            if let data = model.data {
                ActivityReviewCard(review: data.activity)
                WeightReviewCard(review: data.weight, unit: unit, store: services.weight)
                FoodReviewCard(review: data.food)
            } else if model.isLoading {
                ProgressView().frame(maxWidth: .infinity, minHeight: 240)
            }
            if let message = model.errorMessage {
                Text(message).font(AppFont.footnote).foregroundStyle(MetricKind.heart.text)
            }

            shareSection(model)
        }
    }

    @ViewBuilder
    private func shareSection(_ model: ReviewModel) -> some View {
        Button {
            if services.account.isSignedIn {
                showsShare = true
            } else {
                showsSignIn = true
            }
        } label: {
            Label("Share with trainer", systemImage: "square.and.arrow.up")
                .font(AppFont.headline)
                .frame(maxWidth: .infinity, minHeight: TouchTarget.minimum)
        }
        .buttonStyle(.borderedProminent)
        .disabled(!services.account.isAvailable)
        .padding(.top, Spacing.xs)

        if !services.account.isAvailable {
            Text("Sharing needs the server set up in this build (docs/ios/BUILD.md).")
                .font(AppFont.footnote)
                .foregroundStyle(Color.appSecondaryLabel)
        }

        if services.account.isSignedIn {
            Text("ACTIVE LINKS")
                .font(AppFont.footnote.weight(.semibold))
                .foregroundStyle(Color.appSecondaryLabel)
                .padding(.top, Spacing.m)
                .padding(.horizontal, Spacing.xxs)
            if model.links.isEmpty {
                Text("No links yet.")
                    .font(AppFont.subheadline)
                    .foregroundStyle(Color.appSecondaryLabel)
                    .padding(.horizontal, Spacing.xxs)
            } else {
                VStack(spacing: 0) {
                    ForEach(model.links) { link in
                        LinkRow(link: link, url: model.linkService?.url(for: link)) {
                            revoking = link
                        }
                        if link.id != model.links.last?.id {
                            Rectangle().fill(Color.appSeparator).frame(height: 0.5).padding(.leading, Spacing.m)
                        }
                    }
                }
                .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            }
            if let message = model.linksMessage {
                Text(message).font(AppFont.footnote).foregroundStyle(MetricKind.heart.text)
            }
        }
    }
}

// MARK: Cards

private struct ActivityReviewCard: View {
    let review: ActivityReview?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            HStack(alignment: .firstTextBaseline) {
                Text("Activity").font(AppFont.headline)
                Spacer()
                Text("Daily averages · Apple Health")
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
            if let review, review.hasData {
                HStack(alignment: .top) {
                    KeyFigure(title: "Steps", metric: .steps, value: review.averageSteps.map(Format.integer) ?? "–", unit: "")
                    KeyFigure(title: "Sleep", metric: .sleep, value: review.averageSleep.map(Format.duration) ?? "–", unit: "")
                    KeyFigure(title: "Move", metric: .move, value: review.averageMoveKcal.map(Format.integer) ?? "–", unit: "kcal")
                }
                Text("Rings closed on \(review.ringsClosedDays) of \(review.days) days")
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
            } else {
                Text("No Apple Health data for this period.")
                    .font(AppFont.subheadline)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
        }
        .cardPadded()
    }
}

private struct WeightReviewCard: View {
    let review: WeightReview?
    let unit: WeightUnit
    let store: any WeightStore

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            HStack(alignment: .firstTextBaseline) {
                Text("Weight").font(AppFont.headline)
                Spacer()
                if let review, review.entries.count > 1 {
                    Text(WeightFormat.weeklyChange(review.change, unit: unit).replacingOccurrences(of: " this week", with: ""))
                        .font(AppFont.subheadline.weight(.semibold))
                        .numberDesign()
                        .foregroundStyle(MetricKind.weight.text)
                }
            }
            if let review {
                Text("\(WeightFormat.value(review.first.kilograms, unit: unit)) → \(WeightFormat.value(review.last.kilograms, unit: unit)) \(unit.rawValue) · \(review.entries.count) weigh-in\(review.entries.count == 1 ? "" : "s")")
                    .font(AppFont.subheadline)
                    .numberDesign()
                    .foregroundStyle(Color.appSecondaryLabel)
                if review.firstPhoto != nil || review.lastPhoto != nil {
                    HStack(spacing: Spacing.s) {
                        if let first = review.firstPhoto {
                            photo(first.photoID, caption: "First · \(first.entry.day.formatted(.dateTime.day().month(.abbreviated)))")
                        }
                        if let last = review.lastPhoto {
                            photo(last.photoID, caption: "Last · \(last.entry.day.formatted(.dateTime.day().month(.abbreviated)))")
                        }
                    }
                }
            } else {
                Text("No weigh-ins in this period.")
                    .font(AppFont.subheadline)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
        }
        .cardPadded()
    }

    private func photo(_ id: UUID, caption: String) -> some View {
        Color.clear
            .aspectRatio(3 / 4, contentMode: .fit)
            .overlay { PhotoThumbnail(photo: .stored(id), store: store, maxPixelSize: 500) }
            .overlay(alignment: .bottomLeading) {
                Text(caption)
                    .font(AppFont.caption.weight(.semibold))
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.6), radius: 2)
                    .padding(Spacing.xs)
            }
            .clipShape(RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
            .frame(maxWidth: .infinity)
    }
}

private struct FoodReviewCard: View {
    let review: FoodReview

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            HStack(alignment: .firstTextBaseline) {
                Text("Food").font(AppFont.headline)
                Spacer()
                Text("\(review.daysWithPhotos) of \(review.days) days with photos")
                    .font(AppFont.subheadline)
                    .numberDesign()
                    .foregroundStyle(Color.appSecondaryLabel)
            }
            MetricProgressBar(
                progress: review.days > 0 ? Double(review.daysWithPhotos) / Double(review.days) : 0,
                color: MetricKind.food.graphic
            )
        }
        .cardPadded()
        .accessibilityElement(children: .combine)
    }
}

private struct LinkRow: View {
    let link: ShareLinkSummary
    let url: URL?
    let onRevoke: () -> Void

    var body: some View {
        HStack(spacing: Spacing.s) {
            VStack(alignment: .leading, spacing: 2) {
                Text(link.trainerName.isEmpty ? "Trainer" : link.trainerName)
                    .font(AppFont.body)
                    .foregroundStyle(Color.appLabel)
                Text(detail)
                    .font(AppFont.footnote)
                    .foregroundStyle(link.isExpired(now: Date()) ? MetricKind.heart.text : Color.appSecondaryLabel)
            }
            Spacer(minLength: Spacing.xs)
            if let url {
                ShareLink(item: url) {
                    Image(systemName: "square.and.arrow.up")
                        .frame(width: TouchTarget.minimum, height: TouchTarget.minimum)
                }
                .accessibilityLabel("Share link again")
            }
            Button("Revoke", role: .destructive, action: onRevoke)
                .font(AppFont.subheadline.weight(.semibold))
                .frame(minHeight: TouchTarget.minimum)
        }
        .padding(.horizontal, Spacing.m)
        .padding(.vertical, Spacing.xs)
    }

    private var detail: String {
        let expiry: String
        if let expiresAt = link.expiresAt {
            // Stored as the start of the day after the last day.
            let lastDay = expiresAt.addingTimeInterval(-1)
            let text = lastDay.formatted(.dateTime.day().month(.abbreviated).year())
            expiry = link.isExpired(now: Date()) ? "expired \(text)" : "expires \(text)"
        } else {
            expiry = "no expiry"
        }
        return "\(link.periodLabel) · \(expiry)"
    }
}

#Preview {
    ReviewTabView()
        .environment(AppServices.preview)
}
