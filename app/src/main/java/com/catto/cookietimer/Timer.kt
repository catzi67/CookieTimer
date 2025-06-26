// --- src/main/java/com/catto/cookietimer/Timer.kt ---
package com.catto.cookietimer

import android.os.CountDownTimer
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

// Room Entity: Represents a table in your database.
// tableName specifies the name of the table.
@Entity(tableName = "timers")
data class Timer @JvmOverloads constructor( // Added @JvmOverloads to generate overloaded constructors for Java/Room
    // PrimaryKey automatically generates unique IDs for new timers.
    // autoGenerate = true ensures that Room generates the ID.
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L, // Changed from val to var, and ensuring a default value
    var name: String = "", // Changed from val to var to provide a setter for Room
    var initialDurationSeconds: Int = 0, // Changed from val to var to provide a setter for Room
    var remainingTimeSeconds: Int = 0, // Added default value
    var isRunning: Boolean = false, // Added default value
    var isCompleted: Boolean = false, // Added default value
    var temperatureCelsius: Double? = null, // Optional cooking temperature, always stored in Celsius
    // @Ignore tells Room to not persist this field in the database.
    // CountDownTimer is a UI-related object and should not be saved.
    @Ignore @Transient var countDownTimer: CountDownTimer? = null
)
