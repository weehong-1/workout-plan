import Foundation
import Observation

/// The app's stores and the account, shared through the environment.
@MainActor
@Observable
final class AppServices {
    let health: any HealthStore
    let weight: any WeightStore
    let food: any FoodStore
    let account: AccountModel

    init(health: any HealthStore, weight: any WeightStore, food: any FoodStore, account: AccountModel) {
        self.health = health
        self.weight = weight
        self.food = food
        self.account = account
    }

    static func live() -> AppServices {
        let local = LocalStoresFactory.make()
        let client = BackendConfig.current.map { SupabaseClient(config: $0) }
        return AppServices(
            health: HealthStoreFactory.make(),
            weight: local.weight,
            food: local.food,
            account: AccountModel(client: client)
        )
    }

    #if DEBUG
    static let preview = AppServices(
        health: SampleHealthStore(),
        weight: InMemoryWeightStore.sample(),
        food: InMemoryFoodStore(),
        account: AccountModel(client: nil)
    )
    #endif
}

/// Sign-in state for the trainer link (spec 5.1, D10). Signing in is optional:
/// everything except sharing works without an account.
@MainActor
@Observable
final class AccountModel {
    let client: SupabaseClient?
    private(set) var email: String?
    private(set) var isSignedIn = false

    init(client: SupabaseClient?) {
        self.client = client
    }

    var isAvailable: Bool { client != nil }

    func refresh() async {
        guard let client else { return }
        let session = await client.currentSession
        email = session?.email
        isSignedIn = session != nil
    }

    func sendCode(to email: String) async throws {
        guard let client else { throw BackendError.notConfigured }
        try await client.sendCode(to: email)
    }

    func verify(email: String, code: String) async throws {
        guard let client else { throw BackendError.notConfigured }
        try await client.verify(email: email, code: code)
        await refresh()
    }

    func signOut() async {
        await client?.signOut()
        await refresh()
    }

    /// Deletes the server account (shared with the web app) and every file it
    /// uploaded, then forgets the session. Same steps as the web app.
    func deleteAccount() async throws {
        guard let client else { throw BackendError.notConfigured }
        let session = try await client.validSession()
        let files = try await client.listObjects(in: session.userFolder)
        try await client.removeObjects(files)
        struct Body: Encodable { let expected_account_id: String; let confirmation: String }
        let body = try JSONEncoder().encode(Body(expected_account_id: session.userFolder, confirmation: "DELETE"))
        _ = try await client.rpc("delete_own_account", json: body)
        await client.clearSession()
        Keychain.deleteAll(withPrefix: TrainerLinkService.tokenKeyPrefix)
        await refresh()
    }
}
