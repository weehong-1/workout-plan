package com.vitrack.app.di

import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.activity.SampleActivityRepository
import com.vitrack.app.data.profile.ProfileRepository
import com.vitrack.app.data.profile.SampleProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindActivityRepository(impl: SampleActivityRepository): ActivityRepository

    @Binds
    abstract fun bindProfileRepository(impl: SampleProfileRepository): ProfileRepository

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
