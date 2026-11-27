package com.vitrack.app.data.home

import kotlinx.coroutines.flow.Flow

/**
 * Model layer for the home feature. ViewModels depend on this interface,
 * never on a concrete data source, so it can be faked in tests.
 */
interface HomeRepository {
    fun observeGreeting(): Flow<String>
}
