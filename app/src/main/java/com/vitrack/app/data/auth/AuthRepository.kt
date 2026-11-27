package com.vitrack.app.data.auth

import com.vitrack.app.data.backend.Backend
import com.vitrack.app.data.backend.BackendException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The signed-in account. */
data class Account(val userId: String, val email: String?)

/**
 * Optional sign-in (spec D11) with an emailed code, the same account as the
 * web app. Everything except sharing works without it.
 */
interface AuthRepository {
    /** False when this build has no server set up. */
    val isAvailable: Boolean

    val account: Flow<Account?>

    suspend fun sendCode(email: String)

    suspend fun verify(email: String, code: String)

    suspend fun signOut()
}

@Singleton
class SupabaseAuthRepository @Inject constructor(private val backend: Backend) : AuthRepository {

    override val isAvailable: Boolean get() = backend.isConfigured

    override val account: Flow<Account?> =
        backend.client?.session?.map { session -> session?.let { Account(it.userId, it.email) } } ?: flowOf(null)

    override suspend fun sendCode(email: String) = client().sendCode(email)

    override suspend fun verify(email: String, code: String) {
        client().verify(email, code)
    }

    override suspend fun signOut() {
        backend.client?.signOut()
    }

    private fun client() = backend.client ?: throw BackendException.NotConfigured()
}
