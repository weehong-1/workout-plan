import Foundation

/// Where the existing web app's Supabase project and site live (spec D10).
/// Values come from Info.plist, filled from the target's user-defined build
/// settings (SUPABASE_HOST, SUPABASE_PUBLISHABLE_KEY, WEB_APP_HOST). They are
/// public config, like the web app's NEXT_PUBLIC_SUPABASE_* values; row level
/// security protects the data.
struct BackendConfig: Sendable {
    let supabaseURL: URL
    let publishableKey: String
    /// Base of trainer links: https://<host>/share/<token>. Nil until the web
    /// host is set; sign-in still works, sharing waits for it.
    let webAppURL: URL?

    /// Nil while the build settings still have the Supabase placeholders, so the app
    /// runs without a server and hides sign-in and sharing.
    static let current: BackendConfig? = fromBundle()

    static func fromBundle(_ bundle: Bundle = .main) -> BackendConfig? {
        func value(_ key: String) -> String? {
            guard let raw = bundle.object(forInfoDictionaryKey: key) as? String else { return nil }
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            let isPlaceholder = trimmed.isEmpty || trimmed.contains("your-") || trimmed.contains("xxxx") || trimmed.hasPrefix("$(")
            return isPlaceholder ? nil : trimmed
        }
        guard let supabaseHost = value("SupabaseHost"),
              let key = value("SupabasePublishableKey"),
              let supabaseURL = URL(string: "https://\(supabaseHost)")
        else { return nil }
        let webAppURL = value("WebAppHost").flatMap { URL(string: "https://\($0)") }
        return BackendConfig(supabaseURL: supabaseURL, publishableKey: key, webAppURL: webAppURL)
    }
}
