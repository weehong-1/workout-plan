import SwiftUI

/// Weight, Food and Review until their phase is built.
struct PlaceholderTabView: View {
    let title: String

    var body: some View {
        NavigationStack {
            ScrollView {
                Text("Coming in a later phase")
                    .font(AppFont.body)
                    .foregroundStyle(Color.appSecondaryLabel)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, Spacing.m)
                    .padding(.top, Spacing.xs)
            }
            .background(Color.appBackground)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.large)
            .accountToolbar()
        }
    }
}

#Preview {
    PlaceholderTabView(title: "Weight")
}
