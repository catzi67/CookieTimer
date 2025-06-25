// --- src/main/java/com/catto/cookietimer/Timer.kt ---
package com.catto.cookietimer

import android.os.CountDownTimer
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

// Room Entity: Represents a table in your database.
// tableName specifies the name of the table.
@Entity(tableName = "timers")
data class Timer @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    var name: String = "",
    var initialDurationSeconds: Int = 0,
    var remainingTimeSeconds: Int = 0, // This will be the source of truth for resume
    var isRunning: Boolean = false,
    var isCompleted: Boolean = false,
    var temperatureCelsius: Double? = null,
    var originalInputUnit: String? = null,
    // CRUCIAL: Use lastStartedTimestamp to calculate elapsed time for resume/stop.
    var lastStartedTimestamp: Long? = null, // Timestamp when the timer was last started (System.currentTimeMillis())
    // @Ignore tells Room to not persist this field in the database.
    // CountDownTimer is a UI-related object and should not be saved.
    @Ignore @Transient var countDownTimer: CountDownTimer? = null
)
