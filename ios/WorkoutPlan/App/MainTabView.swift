import SwiftUI

enum AppTab: Hashable {
    case activity, weight, food, review
}

/// System tab bar with the four modules (spec 4). The system tab bar carries
/// the iOS 27 Liquid Glass look; there is no custom tab bar.
struct MainTabView: View {
    let store: any HealthStore
    @State private var selection: AppTab = .activity

    var body: some View {
        TabView(selection: $selection) {
            Tab("Activity", systemImage: "circle.circle", value: AppTab.activity) {
                ActivityDashboardView(store: store)
            }
            Tab("Weight", systemImage: "scalemass", value: AppTab.weight) {
                PlaceholderTabView(title: "Weight")
            }
            Tab("Food", systemImage: "fork.knife", value: AppTab.food) {
                PlaceholderTabView(title: "Food")
            }
            Tab("Review", systemImage: "chart.bar.doc.horizontal", value: AppTab.review) {
                PlaceholderTabView(title: "Review")
            }
        }
        .tint(Color.appTint)
    }
}

#Preview {
    MainTabView(store: SampleHealthStore())
}
