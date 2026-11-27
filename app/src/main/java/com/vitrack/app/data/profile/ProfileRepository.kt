package com.vitrack.app.data.profile

import com.vitrack.app.data.auth.AuthRepository
import com.vitrack.app.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface ProfileRepository {
    /** The name used in greetings and the avatar, or null when there is none. */
    fun observeDisplayName(): Flow<String?>
}

/** The name typed in Profile, else the account's (Google's first name, or the email before the @). */
@Singleton
class AccountProfileRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
) : ProfileRepository {
    override fun observeDisplayName(): Flow<String?> =
        combine(settings.displayName, auth.account) { typed, account ->
            typed ?: account?.name?.substringBefore(' ')?.ifBlank { null }
                ?: account?.email?.substringBefore('@')?.replaceFirstChar { it.uppercase() }?.ifBlank { null }
        }
}
