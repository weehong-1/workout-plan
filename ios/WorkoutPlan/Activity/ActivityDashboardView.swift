import SwiftUI

enum ActivityRoute: Hashable {
    case steps
}

/// Activity tab (spec 6.1, design `Activity.dc.html`).
struct ActivityDashboardView: View {
    let store: any HealthStore

    @State private var model: ActivityViewModel
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .body) private var tileHeight: CGFloat = 172

    init(store: any HealthStore) {
        self.store = store
        _model = State(initialValue: ActivityViewModel(store: store))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    header
                    content
                }
                .padding(.horizontal, Spacing.m)
                .padding(.bottom, Spacing.xl)
            }
            .background(Color.appBackground)
            .navigationTitle("Activity")
            .navigationBarTitleDisplayMode(.inline)
            .accountToolbar()
            .refreshable { await model.load() }
            .task { await model.load() }
            .onChange(of: scenePhase) { _, phase in
                // New Watch data usually arrives while the app is in the background.
                if phase == .active {
                    Task { await model.load() }
                }
            }
            .navigationDestination(for: ActivityRoute.self) { route in
                switch route {
                case .steps:
                    StepsDetailView(store: store, stepGoal: GoalMath.defaultStepGoal)
                }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(Format.longDate(Date()).uppercased())
                .font(AppFont.footnote.weight(.semibold))
                .foregroundStyle(Color.appSecondaryLabel)
            Text("Good day, \(UserProfile.firstName)")
                .font(AppFont.largeTitle)
                .foregroundStyle(Color.appLabel)
                .accessibilityAddTraits(.isHeader)
        }
        .padding(.horizontal, Spacing.xxs)
        .padding(.top, Spacing.xs)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, minHeight: 320)
        case .unavailable:
            HealthUnavailableView()
        case .needsAccess:
            NoHealthAccessView { await model.requestAccess() }
        case .failed(let message):
            ActivityErrorView(message: message) { await model.load() }
        case .loaded(let snapshot):
            if snapshot.isEmpty {
                NoActivityDataView()
            } else {
                dashboard(snapshot)
            }
        }
    }

    @ViewBuilder
    private func dashboard(_ snapshot: ActivitySnapshot) -> some View {
        if snapshot.isHistoryLimited {
            LimitedHistoryBanner()
        }

        HeroRingsCard(today: snapshot.today, week: snapshot.week)

        VStack(alignment: .leading, spacing: Spacing.s) {
            SectionHeader(title: "Highlights")
            highlights(snapshot)
        }

        WeeklyStepsCard(days: snapshot.weeklySteps, goal: snapshot.stepGoal)
    }

    @ViewBuilder
    private func highlights(_ snapshot: ActivitySnapshot) -> some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(spacing: Spacing.s) {
                stepsCard(snapshot)
                SleepHighlightCard(night: snapshot.sleep)
                HeartHighlightCard(heart: snapshot.heart)
                WorkoutsHighlightCard(workouts: snapshot.workouts)
            }
        } else {
            Grid(horizontalSpacing: Spacing.s, verticalSpacing: Spacing.s) {
                GridRow {
                    stepsCard(snapshot)
                        .frame(minHeight: tileHeight)
                    SleepHighlightCard(night: snapshot.sleep)
                        .frame(minHeight: tileHeight)
                }
                GridRow {
                    HeartHighlightCard(heart: snapshot.heart)
                        .frame(minHeight: tileHeight)
                    WorkoutsHighlightCard(workouts: snapshot.workouts)
                        .frame(minHeight: tileHeight)
                }
            }
        }
    }

    private func stepsCard(_ snapshot: ActivitySnapshot) -> some View {
        NavigationLink(value: ActivityRoute.steps) {
            StepsHighlightCard(steps: snapshot.steps, goal: snapshot.stepGoal)
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    ActivityDashboardView(store: SampleHealthStore())
}
