package com.vitrack.app.data.backend

/**
 * Where the existing web app's Supabase project and site live (spec D10).
 * Public client values, like the web app's NEXT_PUBLIC_SUPABASE_* ones; row
 * level security protects the data.
 */
data class BackendConfig(
    /** https://<project>.supabase.co */
    val supabaseUrl: String,
    val publishableKey: String,
    /** Base of trainer links, https://<host>. Null until set; sign-in still works. */
    val webAppUrl: String?,
    /**
     * The Google OAuth "Web application" client ID that Supabase's Google
     * provider uses; Android asks Google for an ID token for it. Null: only
     * the email code works.
     */
    val googleWebClientId: String? = null,
) {
    companion object {
        /**
         * From the build's values (local.properties). Null while the Supabase
         * host or key is empty or still a placeholder, so the app runs without
         * a server and says sharing isn't set up.
         */
        fun from(supabaseHost: String, publishableKey: String, webAppHost: String, googleWebClientId: String = ""): BackendConfig? {
            val host = hostOrNull(supabaseHost) ?: return null
            val key = publishableKey.trim().takeUnless { isPlaceholder(it) } ?: return null
            return BackendConfig(
                supabaseUrl = "https://$host",
                publishableKey = key,
                webAppUrl = hostOrNull(webAppHost)?.let { "https://$it" },
                googleWebClientId = googleWebClientId.trim().takeUnless { isPlaceholder(it) },
            )
        }

        private fun hostOrNull(value: String): String? =
            value.trim().removePrefix("https://").removePrefix("http://").trimEnd('/').takeUnless { isPlaceholder(it) }

        private fun isPlaceholder(value: String): Boolean =
            value.isEmpty() || value.contains("your-") || value.contains("xxxx") || value.contains('<')
    }
}

/** The configured server, or none: lets Hilt hand out "maybe a server" without nullable bindings. */
class Backend(val client: SupabaseClient?) {
    val isConfigured: Boolean get() = client != null
    val webAppUrl: String? get() = client?.config?.webAppUrl
}

sealed class BackendException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConfigured : BackendException("The server isn't set up in this build")
    class NotSignedIn : BackendException("Sign in to continue")
    class Server(val status: Int, message: String) : BackendException(message)
    class Network(cause: Throwable) : BackendException("Couldn't reach the server", cause)
    class InvalidResponse(cause: Throwable? = null) : BackendException("The server sent an unexpected response", cause)
}
