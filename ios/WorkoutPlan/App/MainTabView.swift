import SwiftUI

enum AppTab: Hashable {
    case activity, weight, food, review
}

/// System tab bar with the four modules (spec 4). The system tab bar carries
/// the iOS 27 Liquid Glass look; there is no custom tab bar.
struct MainTabView: View {
    @Environment(AppServices.self) private var services
    @State private var selection: AppTab = .activity

    var body: some View {
        TabView(selection: $selection) {
            Tab("Activity", systemImage: "circle.circle", value: AppTab.activity) {
                ActivityDashboardView(store: services.health)
            }
            Tab("Weight", systemImage: "scalemass", value: AppTab.weight) {
                WeightTabView(store: services.weight)
            }
            Tab("Food", systemImage: "fork.knife", value: AppTab.food) {
                FoodTabView(store: services.food)
            }
            Tab("Review", systemImage: "chart.bar.doc.horizontal", value: AppTab.review) {
                ReviewTabView()
            }
        }
        .tint(Color.appTint)
    }
}

#Preview {
    MainTabView()
        .environment(AppServices.preview)
}
