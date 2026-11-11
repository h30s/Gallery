package com.oxlo.gallary.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaItemEntity::class,
        TagEntity::class,
        MediaTagCrossRef::class,
        PersonEntity::class,
        FaceEntity::class,
        SourceEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class GallaryDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
    abstract fun peopleDao(): PeopleDao
    abstract fun sourceDao(): SourceDao

    companion object {
        @Volatile
        private var instance: GallaryDatabase? = null

        fun get(context: Context): GallaryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                GallaryDatabase::class.java,
                "gallary.db"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
