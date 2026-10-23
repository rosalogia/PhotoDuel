package dev.rosalogia.photoduel.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.rosalogia.photoduel.core.data.db.PhotoDuelDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PhotoDuelDatabase =
        Room.databaseBuilder(context, PhotoDuelDatabase::class.java, "photoduel.db").build()
}
