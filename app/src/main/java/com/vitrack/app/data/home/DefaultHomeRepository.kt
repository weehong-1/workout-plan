package com.vitrack.app.data.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Placeholder implementation. Swap in Room or network data sources here. */
@Singleton
class DefaultHomeRepository @Inject constructor() : HomeRepository {
    override fun observeGreeting(): Flow<String> = flowOf("Welcome to Vitrack")
}
