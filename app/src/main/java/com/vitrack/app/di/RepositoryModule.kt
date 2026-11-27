package com.vitrack.app.di

import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.activity.SampleActivityRepository
import com.vitrack.app.data.food.FoodRepository
import com.vitrack.app.data.food.RoomFoodRepository
import com.vitrack.app.data.profile.ProfileRepository
import com.vitrack.app.data.profile.SampleProfileRepository
import com.vitrack.app.data.share.NotConnectedShareRepository
import com.vitrack.app.data.share.ShareRepository
import com.vitrack.app.data.settings.DataStoreSettingsRepository
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.weight.RoomWeightRepository
import com.vitrack.app.data.weight.WeightRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.temporal.WeekFields
import java.util.Locale

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindActivityRepository(impl: SampleActivityRepository): ActivityRepository

    @Binds
    abstract fun bindProfileRepository(impl: SampleProfileRepository): ProfileRepository

    @Binds
    abstract fun bindWeightRepository(impl: RoomWeightRepository): WeightRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindFoodRepository(impl: RoomFoodRepository): FoodRepository

    // Swap for the Supabase implementation once sign-in and the server are set up.
    @Binds
    abstract fun bindShareRepository(impl: NotConnectedShareRepository): ShareRepository

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()

        /** Weeks follow the phone's calendar, like the date pickers. */
        @Provides
        fun provideWeekFields(): WeekFields = WeekFields.of(Locale.getDefault())
    }
}
