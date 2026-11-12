package com.pouch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Main Room database instance for Pouch.
 */
@Database(
    entities = [GroupEntity::class, MemberEntity::class, ReceiptEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PouchDatabase : RoomDatabase() {
    abstract val dao: PouchDao

    companion object {
        @Volatile
        private var instance: PouchDatabase? = null

        fun getInstance(context: Context): PouchDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PouchDatabase::class.java,
                    "pouch.db",
                ).build().also { instance = it }
            }
        }
    }
}
