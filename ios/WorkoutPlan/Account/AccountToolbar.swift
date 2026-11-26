import SwiftUI

/// Adds the account avatar to the top bar and presents the Account sheet.
private struct AccountToolbar: ViewModifier {
    @State private var isPresented = false

    func body(content: Content) -> some View {
        content
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        isPresented = true
                    } label: {
                        AccountAvatar()
                    }
                    .accessibilityLabel("Account")
                }
            }
            .sheet(isPresented: $isPresented) {
                AccountSheet()
            }
    }
}

extension View {
    /// Account avatar in the top bar of a tab (spec 6.5).
    func accountToolbar() -> some View {
        modifier(AccountToolbar())
    }
}

/// Circle with the user's initial.
struct AccountAvatar: View {
    var body: some View {
        Text(UserProfile.initial)
            .font(AppFont.headline)
            .foregroundStyle(Color.appLabel)
            .frame(width: 32, height: 32)
            .background(Color.appFill, in: Circle())
            .frame(minWidth: TouchTarget.minimum, minHeight: TouchTarget.minimum)
            .contentShape(Rectangle())
    }
}

/// Account sheet. Empty in phase 1: units, Health status, sign out and delete
/// account come later.
struct AccountSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Color.appBackground
                .ignoresSafeArea()
                .navigationTitle("Account")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }
                    }
                }
        }
    }
}
