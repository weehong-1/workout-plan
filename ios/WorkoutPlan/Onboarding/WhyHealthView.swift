import SwiftUI

/// Explains what is read from Apple Health and why, before the system prompt
/// (spec 5.2).
struct WhyHealthView: View {
    let isRequesting: Bool
    let onContinue: () -> Void
    let onSkip: () -> Void

    private struct Item: Identifiable {
        let id: String
        let systemImage: String
        let metric: MetricKind
        let reason: String
    }

    private let items: [Item] = [
        Item(id: "Steps and distance", systemImage: "figure.walk", metric: .steps,
             reason: "Today's steps, your goal progress and trends."),
        Item(id: "Active energy", systemImage: "flame.fill", metric: .move,
             reason: "Calories for your Move ring."),
        Item(id: "Exercise and stand", systemImage: "figure.run", metric: .exercise,
             reason: "Exercise minutes, stand hours and your Activity rings."),
        Item(id: "Heart rate", systemImage: "heart.fill", metric: .heart,
             reason: "Your latest and resting heart rate."),
        Item(id: "Sleep", systemImage: "bed.double.fill", metric: .sleep,
             reason: "Last night's sleep and its stages."),
        Item(id: "Workouts", systemImage: "figure.outdoor.cycle", metric: .exercise,
             reason: "Workouts from your Apple Watch, such as walks and rides."),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                Image(systemName: "heart.text.square.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(MetricKind.heart.graphic)
                    .accessibilityHidden(true)
                    .padding(.top, Spacing.xxl)

                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("Connect Apple Health")
                        .font(AppFont.largeTitle)
                        .foregroundStyle(Color.appLabel)
                        .accessibilityAddTraits(.isHeader)
                    Text("Workout Plan reads your Apple Watch data from Apple Health to show your day and build your monthly and yearly reviews. It only reads; it never writes to Health.")
                        .font(AppFont.body)
                        .foregroundStyle(Color.appSecondaryLabel)
                }

                VStack(alignment: .leading, spacing: Spacing.m) {
                    ForEach(items) { item in
                        HStack(alignment: .top, spacing: Spacing.s) {
                            Image(systemName: item.systemImage)
                                .font(AppFont.headline)
                                .foregroundStyle(item.metric.text)
                                .frame(width: 32, height: 32)
                                .background(item.metric.graphic.opacity(0.16), in: RoundedRectangle(cornerRadius: Radius.control, style: .continuous))
                                .accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.id)
                                    .font(AppFont.headline)
                                    .foregroundStyle(Color.appLabel)
                                Text(item.reason)
                                    .font(AppFont.subheadline)
                                    .foregroundStyle(Color.appSecondaryLabel)
                            }
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
                .padding(Spacing.m)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))

                recommendation

                Label("Nothing leaves your iPhone.", systemImage: "lock.fill")
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
            .padding(.horizontal, Spacing.m)
            .padding(.bottom, Spacing.xl)
        }
        .background(Color.appBackground)
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: Spacing.xs) {
                Button(action: onContinue) {
                    Text("Continue")
                        .font(AppFont.headline)
                        .frame(maxWidth: .infinity, minHeight: TouchTarget.minimum)
                }
                .buttonStyle(.borderedProminent)
                .disabled(isRequesting)

                Button("Not Now", action: onSkip)
                    .font(AppFont.body)
                    .frame(minHeight: TouchTarget.minimum)
                    .disabled(isRequesting)
            }
            .padding(.horizontal, Spacing.m)
            .padding(.top, Spacing.xs)
            .background(Color.appBackground)
        }
    }

    private var recommendation: some View {
        HStack(alignment: .top, spacing: Spacing.s) {
            Image(systemName: "clock.arrow.circlepath")
                .font(AppFont.headline)
                .foregroundStyle(Color.appTint)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text("Choose All Recorded Data")
                    .font(AppFont.headline)
                    .foregroundStyle(Color.appLabel)
                Text("If iOS asks how much history to share, choose All Recorded Data. Your yearly review needs more than the last 30 days.")
                    .font(AppFont.subheadline)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
        }
        .padding(Spacing.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    WhyHealthView(isRequesting: false, onContinue: {}, onSkip: {})
}
