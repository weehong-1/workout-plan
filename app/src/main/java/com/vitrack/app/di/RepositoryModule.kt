package com.vitrack.app.di

import com.vitrack.app.data.home.DefaultHomeRepository
import com.vitrack.app.data.home.HomeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindHomeRepository(impl: DefaultHomeRepository): HomeRepository
}
