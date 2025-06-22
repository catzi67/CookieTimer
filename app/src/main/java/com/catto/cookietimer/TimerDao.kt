// --- src/main/java/com/catto/cookietimer/TimerDao.kt ---
package com.catto.cookietimer

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow // Import Flow for observing data changes

// DAO (Data Access Object): Defines methods for interacting with the database.
@Dao
interface TimerDao {

    // Inserts a new timer into the database.
    // OnConflictStrategy.REPLACE means if a timer with the same primary key exists, it will be replaced.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimer(timer: Timer): Long // suspend for coroutine, returns the new rowId

    // Retrieves all timers from the 'timers' table.
    // Returns a Flow, which means data changes will be observed in real-time.
    @Query("SELECT * FROM timers ORDER BY id DESC") // Order by ID to keep latest timers on top (or adjust as needed)
    fun getAllTimers(): Flow<List<Timer>>

    // Updates an existing timer in the database.
    @Update
    suspend fun updateTimer(timer: Timer)

    // Deletes a specific timer from the database.
    @Delete
    suspend fun deleteTimer(timer: Timer)

    // Deletes a timer by its ID.
    @Query("DELETE FROM timers WHERE id = :timerId")
    suspend fun deleteTimerById(timerId: Long)
}