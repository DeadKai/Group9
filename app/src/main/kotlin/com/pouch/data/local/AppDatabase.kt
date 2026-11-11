package com.pouch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pouch.data.local.dao.ExpenseDao
import com.pouch.data.local.dao.GroupDao
import com.pouch.data.local.dao.MemberDao
import com.pouch.data.local.entity.ExpenseEntity
import com.pouch.data.local.entity.GroupEntity
import com.pouch.data.local.entity.MemberEntity

@Database(
    entities = [
        GroupEntity::class,
        MemberEntity::class,
        ExpenseEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pouch_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
