import SwiftUI
import UIKit

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

/// Account (spec 6.5): sign-in, units, step goal, Health access, sign out and
/// delete account.
struct AccountSheet: View {
    @Environment(AppServices.self) private var services
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @AppStorage(StorageKey.weightUnit) private var unit: WeightUnit = .kg
    @AppStorage(StorageKey.stepGoal) private var stepGoal: Double = GoalMath.defaultStepGoal

    @State private var healthNeedsRequest = false
    @State private var showsSignIn = false
    @State private var confirmsDelete = false
    @State private var deleteConfirmation = ""
    @State private var isWorking = false
    @State private var errorMessage: String?

    private var account: AccountModel { services.account }

    var body: some View {
        NavigationStack {
            List {
                accountSection
                Section("Units and goals") {
                    Picker("Weight", selection: $unit) {
                        ForEach(WeightUnit.allCases) { unit in
                            Text(unit.title).tag(unit)
                        }
                    }
                    Stepper(value: $stepGoal, in: GoalMath.stepGoalRange, step: GoalMath.stepGoalStep) {
                        LabeledContent("Daily step goal", value: Format.integer(stepGoal))
                    }
                }
                healthSection
                if account.isSignedIn {
                    Section {
                        Button("Delete Account…", role: .destructive) {
                            deleteConfirmation = ""
                            confirmsDelete = true
                        }
                        .disabled(isWorking)
                    } footer: {
                        Text("Deletes your Workout Plan account, which the website shares, with all its data and shared links. Weigh-ins and food photos on this iPhone are deleted too.")
                    }
                }
                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundStyle(MetricKind.heart.text)
                    }
                }
            }
            .navigationTitle("Account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task {
                await account.refresh()
                healthNeedsRequest = await services.health.needsAuthorizationRequest()
            }
            .navigationDestination(isPresented: $showsSignIn) {
                SignInView(account: account) {
                    showsSignIn = false
                }
                .navigationBarTitleDisplayMode(.inline)
            }
            .alert("Delete your account?", isPresented: $confirmsDelete) {
                TextField("Type DELETE", text: $deleteConfirmation)
                    .textInputAutocapitalization(.characters)
                Button("Delete", role: .destructive) { deleteAccount() }
                    .disabled(deleteConfirmation != "DELETE")
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This deletes the account on the website too, and can't be undone. Type DELETE to confirm.")
            }
        }
    }

    @ViewBuilder
    private var accountSection: some View {
        Section {
            if !account.isAvailable {
                Text("Sign-in isn't set up in this build.")
                    .foregroundStyle(Color.appSecondaryLabel)
            } else if account.isSignedIn {
                LabeledContent("Signed in as", value: account.email ?? "–")
                Button("Sign Out") {
                    Task {
                        isWorking = true
                        await account.signOut()
                        isWorking = false
                    }
                }
                .disabled(isWorking)
            } else {
                Button("Sign In") { showsSignIn = true }
            }
        } header: {
            Text("Account")
        } footer: {
            Text("Weigh-ins and food photos are stored on this iPhone. An account is needed only to share a review with a trainer.")
        }
    }

    private var healthSection: some View {
        Section {
            if !services.health.isAvailable {
                Text("Apple Health isn't available on this device.")
            } else if healthNeedsRequest {
                Button("Connect Apple Health") {
                    Task {
                        try? await services.health.requestAuthorization()
                        healthNeedsRequest = await services.health.needsAuthorizationRequest()
                    }
                }
            } else {
                LabeledContent("Apple Health", value: "Asked")
                Button("Manage in Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        openURL(url)
                    }
                }
            }
        } header: {
            Text("Apple Health")
        } footer: {
            Text("Read only. To change what is shared, or to share All Recorded Data, open Settings › Privacy & Security › Health › Workout Plan.")
        }
    }

    private func deleteAccount() {
        isWorking = true
        errorMessage = nil
        Task {
            do {
                try await account.deleteAccount()
                try await services.weight.deleteAll()
                try await services.food.deleteAll()
                dismiss()
            } catch {
                errorMessage = "Couldn't delete the account: \(error.localizedDescription)"
            }
            isWorking = false
        }
    }
}
