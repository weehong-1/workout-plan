import Foundation

/// A signed-in Supabase session.
struct AuthSession: Codable, Sendable {
    let accessToken: String
    let refreshToken: String
    let expiresAt: Date
    let userID: UUID
    let email: String?

    /// `auth.uid()::text` in Postgres and storage folder names are lowercase.
    var userFolder: String { userID.uuidString.lowercased() }
}

enum BackendError: LocalizedError {
    case notConfigured
    case notSignedIn
    case server(status: Int, message: String)
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .notConfigured: "The server isn't set up in this build. See docs/ios/BUILD.md."
        case .notSignedIn: "Sign in to continue."
        case .server(_, let message): message
        case .invalidResponse: "The server sent an unexpected response."
        }
    }
}

/// Minimal Supabase client over plain HTTPS (Auth, PostgREST, Storage), so
/// the app needs no third-party package. The session is kept in the keychain.
actor SupabaseClient {
    let config: BackendConfig
    private var session: AuthSession?
    private let urlSession = URLSession(configuration: .ephemeral)
    private static let sessionKey = "supabase.session"

    init(config: BackendConfig) {
        self.config = config
        if let data = Keychain.data(for: Self.sessionKey) {
            session = try? Self.decoder.decode(AuthSession.self, from: data)
        }
    }

    var currentSession: AuthSession? { session }

    // MARK: Auth (email code, as in the web app)

    func sendCode(to email: String) async throws {
        struct Body: Encodable { let email: String; let create_user: Bool }
        _ = try await send(
            "POST", path: "/auth/v1/otp",
            body: try JSONEncoder().encode(Body(email: email, create_user: true)),
            authorized: false
        )
    }

    @discardableResult
    func verify(email: String, code: String) async throws -> AuthSession {
        struct Body: Encodable { let type = "email"; let email: String; let token: String }
        let data = try await send(
            "POST", path: "/auth/v1/verify",
            body: try JSONEncoder().encode(Body(email: email, token: code)),
            authorized: false
        )
        return try store(tokenResponse: data)
    }

    func signOut() async {
        if session != nil {
            _ = try? await send("POST", path: "/auth/v1/logout", body: nil, authorized: true)
        }
        clearSession()
    }

    /// Forgets the session without calling the server (after account deletion).
    func clearSession() {
        session = nil
        Keychain.set(nil, for: Self.sessionKey)
    }

    /// The session, refreshed first when it expires within a minute.
    func validSession() async throws -> AuthSession {
        guard let current = session else { throw BackendError.notSignedIn }
        if current.expiresAt.timeIntervalSinceNow > 60 { return current }
        struct Body: Encodable { let refresh_token: String }
        do {
            let data = try await send(
                "POST", path: "/auth/v1/token", query: [URLQueryItem(name: "grant_type", value: "refresh_token")],
                body: try JSONEncoder().encode(Body(refresh_token: current.refreshToken)),
                authorized: false
            )
            return try store(tokenResponse: data)
        } catch BackendError.server(let status, _) where status == 400 || status == 401 {
            clearSession()
            throw BackendError.notSignedIn
        }
    }

    private func store(tokenResponse data: Data) throws -> AuthSession {
        struct User: Decodable { let id: UUID; let email: String? }
        struct TokenResponse: Decodable {
            let access_token: String
            let refresh_token: String
            let expires_in: Double
            let user: User
        }
        let response = try JSONDecoder().decode(TokenResponse.self, from: data)
        let newSession = AuthSession(
            accessToken: response.access_token,
            refreshToken: response.refresh_token,
            expiresAt: Date().addingTimeInterval(response.expires_in),
            userID: response.user.id,
            email: response.user.email
        )
        session = newSession
        Keychain.set(try Self.encoder.encode(newSession), for: Self.sessionKey)
        return newSession
    }

    // MARK: Database (PostgREST)

    func select(_ table: String, query: [URLQueryItem]) async throws -> Data {
        try await send("GET", path: "/rest/v1/\(table)", query: query, body: nil, authorized: true)
    }

    func insert(_ table: String, json: Data) async throws {
        _ = try await send("POST", path: "/rest/v1/\(table)", body: json, authorized: true, headers: ["Prefer": "return=minimal"])
    }

    func delete(_ table: String, query: [URLQueryItem]) async throws {
        _ = try await send("DELETE", path: "/rest/v1/\(table)", query: query, body: nil, authorized: true)
    }

    func rpc(_ function: String, json: Data) async throws -> Data {
        try await send("POST", path: "/rest/v1/rpc/\(function)", body: json, authorized: true)
    }

    // MARK: Storage (private "photos" bucket, one folder per account)

    func upload(jpeg: Data, path: String) async throws {
        _ = try await send(
            "POST", path: "/storage/v1/object/photos/\(path)",
            body: jpeg, authorized: true,
            headers: ["Content-Type": "image/jpeg", "x-upsert": "true"]
        )
    }

    func removeObjects(_ paths: [String]) async throws {
        guard !paths.isEmpty else { return }
        struct Body: Encodable { let prefixes: [String] }
        for chunk in stride(from: 0, to: paths.count, by: 500).map({ Array(paths[$0..<min($0 + 500, paths.count)]) }) {
            _ = try await send("DELETE", path: "/storage/v1/object/photos", body: try JSONEncoder().encode(Body(prefixes: chunk)), authorized: true)
        }
    }

    /// Files directly inside `folder` (not sub-folders), as full paths.
    func listObjects(in folder: String) async throws -> [String] {
        struct Body: Encodable { let prefix: String; let limit: Int; let offset: Int }
        struct Item: Decodable { let name: String; let id: String? }
        var paths: [String] = []
        var offset = 0
        while true {
            let data = try await send(
                "POST", path: "/storage/v1/object/list/photos",
                body: try JSONEncoder().encode(Body(prefix: folder, limit: 1_000, offset: offset)),
                authorized: true
            )
            let items = try JSONDecoder().decode([Item].self, from: data)
            paths += items.filter { $0.id != nil }.map { "\(folder)/\($0.name)" }
            if items.count < 1_000 { break }
            offset += items.count
        }
        return paths
    }

    // MARK: Transport

    private func send(
        _ method: String,
        path: String,
        query: [URLQueryItem] = [],
        body: Data?,
        authorized: Bool,
        headers: [String: String] = [:]
    ) async throws -> Data {
        guard var components = URLComponents(url: config.supabaseURL.appending(path: path), resolvingAgainstBaseURL: false) else {
            throw BackendError.invalidResponse
        }
        if !query.isEmpty { components.queryItems = query }
        guard let url = components.url else { throw BackendError.invalidResponse }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue(config.publishableKey, forHTTPHeaderField: "apikey")
        if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        if authorized {
            let token = try await validSession().accessToken
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        for (name, value) in headers {
            request.setValue(value, forHTTPHeaderField: name)
        }

        let (data, response) = try await urlSession.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw BackendError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            throw BackendError.server(status: http.statusCode, message: Self.message(from: data, status: http.statusCode))
        }
        return data
    }

    /// Supabase services name their error field differently.
    private static func message(from data: Data, status: Int) -> String {
        if let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            for key in ["msg", "message", "error_description", "error"] {
                if let text = object[key] as? String, !text.isEmpty { return text }
            }
        }
        return "The server returned an error (\(status))."
    }

    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}
