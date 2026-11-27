package com.vitrack.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.vitrack.app.data.food.FoodPhotos
import com.vitrack.app.data.food.db.FoodDao
import com.vitrack.app.data.food.db.MIGRATION_1_2
import com.vitrack.app.data.weight.AppPhotoStore
import com.vitrack.app.data.weight.PhotoStore
import com.vitrack.app.data.weight.db.VitrackDatabase
import com.vitrack.app.data.weight.db.WeightDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VitrackDatabase =
        Room.databaseBuilder(context, VitrackDatabase::class.java, "vitrack.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideWeightDao(db: VitrackDatabase): WeightDao = db.weightDao()

    @Provides
    fun provideFoodDao(db: VitrackDatabase): FoodDao = db.foodDao()

    /** Progress photos (weigh-ins). Unqualified, as Weight was built first. */
    @Provides
    @Singleton
    fun provideWeightPhotoStore(@ApplicationContext context: Context): PhotoStore =
        AppPhotoStore(context, "weight_photos")

    @Provides
    @Singleton
    @FoodPhotos
    fun provideFoodPhotoStore(@ApplicationContext context: Context): PhotoStore =
        AppPhotoStore(context, "food_photos")

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
