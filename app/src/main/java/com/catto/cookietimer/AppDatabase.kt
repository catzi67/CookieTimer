// --- src/main/java/com.catto/cookietimer/AppDatabase.kt ---
package com.catto.cookietimer

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Room Database: The main access point for the underlying database connection.
// entities: Lists all the Room Entities (tables) in this database.
// version: Database version. Increment this when you change the schema (add/remove tables/columns).
// exportSchema: Set to false for simple apps to avoid creating schema export folders.
@Database(entities = [Timer::class], version = 4, exportSchema = false) // Version incremented to 4 due to Timer entity changes
abstract class AppDatabase : RoomDatabase() {

    // Abstract method to get the DAO. Room generates the implementation.
    abstract fun timerDao(): TimerDao

    companion object {
        // Singleton instance of the database to prevent multiple instances
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Returns the singleton instance of the database.
        // If it doesn't exist, it creates one.
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) { // Synchronized to prevent multiple threads from creating instances
                val instance = Room.databaseBuilder(
                    context.applicationContext, // Use application context to prevent memory leaks
                    AppDatabase::class.java,
                    "cookie_timer_db" // Name of your database file
                )
                    // Reverting to the parameterless fallbackToDestructiveMigration() as it's typically functionally stable for development
                    // despite deprecation warnings in newer IDEs. This allows Room to recreate the database destructively
                    // on schema changes (like new columns in Timer entity) when no explicit migrations are provided.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
