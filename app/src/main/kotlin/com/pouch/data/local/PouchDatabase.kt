package com.pouch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        User::class,
        GroupEntity::class,
        MemberEntity::class,
        ReceiptEntity::class,
        ReceiptItemEntity::class,
        ReceiptItemAssigneeEntity::class,
        ReceiptPayerEntity::class,
        ReceiptSplitEntity::class,
        FriendshipEntity::class,
    ],
    version = 2,
    exportSchema = false
)
abstract class PouchDatabase : RoomDatabase() {

    abstract fun pouchDao(): PouchDao

    companion object {
        @Volatile
        private var INSTANCE: PouchDatabase? = null

        fun getDatabase(context: Context): PouchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PouchDatabase::class.java,
                    "pouch.db",
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}