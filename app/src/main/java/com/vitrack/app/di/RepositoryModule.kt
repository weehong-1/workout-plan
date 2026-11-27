package com.vitrack.app.di

import com.vitrack.app.data.activity.ActivityRepository
import com.vitrack.app.data.activity.SampleActivityRepository
import com.vitrack.app.data.auth.AuthRepository
import com.vitrack.app.data.auth.SupabaseAuthRepository
import com.vitrack.app.data.food.FoodRepository
import com.vitrack.app.data.food.RoomFoodRepository
import com.vitrack.app.data.profile.ProfileRepository
import com.vitrack.app.data.profile.AccountProfileRepository
import com.vitrack.app.data.account.LocalData
import com.vitrack.app.data.account.RoomLocalData
import com.vitrack.app.data.reminders.AlarmReminderScheduler
import com.vitrack.app.data.reminders.ReminderScheduler
import com.vitrack.app.data.settings.DataStoreSettingsRepository
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.share.BitmapSharePhotoEncoder
import com.vitrack.app.data.share.RepositoryShareSourceLoader
import com.vitrack.app.data.share.SharePhotoEncoder
import com.vitrack.app.data.share.ShareRepository
import com.vitrack.app.data.share.ShareSourceLoader
import com.vitrack.app.data.share.SupabaseShareRepository
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
    abstract fun bindProfileRepository(impl: AccountProfileRepository): ProfileRepository

    @Binds
    abstract fun bindLocalData(impl: RoomLocalData): LocalData

    @Binds
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    abstract fun bindWeightRepository(impl: RoomWeightRepository): WeightRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindFoodRepository(impl: RoomFoodRepository): FoodRepository

    @Binds
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    /** Without a server in the build, it reports sharing isn't set up and never fakes a link. */
    @Binds
    abstract fun bindShareRepository(impl: SupabaseShareRepository): ShareRepository

    @Binds
    abstract fun bindShareSourceLoader(impl: RepositoryShareSourceLoader): ShareSourceLoader

    @Binds
    abstract fun bindSharePhotoEncoder(impl: BitmapSharePhotoEncoder): SharePhotoEncoder

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()

        /** Weeks follow the phone's calendar, like the date pickers. */
        @Provides
        fun provideWeekFields(): WeekFields = WeekFields.of(Locale.getDefault())
    }
}
