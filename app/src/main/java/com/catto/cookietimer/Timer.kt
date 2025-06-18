package com.catto.cookietimer

import android.os.CountDownTimer

// Data class to represent a single timer's state
data class Timer(
    val id: Long, // Unique ID for each timer
    val name: String,
    val initialDurationSeconds: Int, // Total duration in seconds
    var remainingTimeSeconds: Int, // Current remaining time in seconds
    var isRunning: Boolean,
    var isCompleted: Boolean,
    @Transient var countDownTimer: CountDownTimer? = null // Transient to avoid serialization issues if saving state
)
