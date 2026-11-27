package com.vitrack.app.data.profile

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface ProfileRepository {
    /** The name used in greetings, or null when not set. */
    fun observeDisplayName(): Flow<String?>
}

/** Placeholder until Account and sign-in exist. */
@Singleton
class SampleProfileRepository @Inject constructor() : ProfileRepository {
    override fun observeDisplayName(): Flow<String?> = flowOf("Vernon")
}
