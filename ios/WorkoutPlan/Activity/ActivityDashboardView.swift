import SwiftUI

enum ActivityRoute: Hashable {
    case steps, sleep, heart, workouts
}

/// Activity tab (spec 6.1, design `Activity.dc.html`).
struct ActivityDashboardView: View {
    let store: any HealthStore

    @State private var model: ActivityViewModel
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .body) private var tileHeight: CGFloat = 172
    @AppStorage(StorageKey.stepGoal) private var stepGoal: Double = GoalMath.defaultStepGoal

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
            .refreshable { await model.load(stepGoal: stepGoal) }
            .task { await model.load(stepGoal: stepGoal) }
            .onChange(of: stepGoal) {
                Task { await model.load(stepGoal: stepGoal) }
            }
            .onChange(of: scenePhase) { _, phase in
                // New Watch data usually arrives while the app is in the background.
                if phase == .active {
                    Task { await model.load(stepGoal: stepGoal) }
                }
            }
            .navigationDestination(for: ActivityRoute.self) { route in
                switch route {
                case .steps:
                    StepsDetailView(store: store, stepGoal: stepGoal)
                case .sleep:
                    SleepDetailView(store: store)
                case .heart:
                    HeartDetailView(store: store)
                case .workouts:
                    WorkoutsDetailView(store: store)
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
            NoHealthAccessView { await model.requestAccess(stepGoal: stepGoal) }
        case .failed(let message):
            ActivityErrorView(message: message) { await model.load(stepGoal: stepGoal) }
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
                link(.sleep) { SleepHighlightCard(night: snapshot.sleep) }
                link(.heart) { HeartHighlightCard(heart: snapshot.heart) }
                link(.workouts) { WorkoutsHighlightCard(workouts: snapshot.workouts) }
            }
        } else {
            Grid(horizontalSpacing: Spacing.s, verticalSpacing: Spacing.s) {
                GridRow {
                    stepsCard(snapshot)
                        .frame(minHeight: tileHeight)
                    link(.sleep) { SleepHighlightCard(night: snapshot.sleep) }
                        .frame(minHeight: tileHeight)
                }
                GridRow {
                    link(.heart) { HeartHighlightCard(heart: snapshot.heart) }
                        .frame(minHeight: tileHeight)
                    link(.workouts) { WorkoutsHighlightCard(workouts: snapshot.workouts) }
                        .frame(minHeight: tileHeight)
                }
            }
        }
    }

    private func stepsCard(_ snapshot: ActivitySnapshot) -> some View {
        link(.steps) {
            StepsHighlightCard(steps: snapshot.steps, goal: snapshot.stepGoal)
        }
    }

    private func link<Card: View>(_ route: ActivityRoute, @ViewBuilder card: () -> Card) -> some View {
        NavigationLink(value: route) {
            card()
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    ActivityDashboardView(store: SampleHealthStore())
        .environment(AppServices.preview)
}
