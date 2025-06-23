
// --- src/main/java/com/catto/cookietimer/TimerService.kt ---
package com.catto.cookietimer

import android.app.Service
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import android.app.Notification // Import Notification class
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import android.media.MediaPlayer // Import MediaPlayer
import android.os.VibrationEffect // Import VibrationEffect
import android.os.Vibrator // Import Vibrator
import com.catto.cookietimer.AppDatabase
import com.catto.cookietimer.TimerDao
import android.app.NotificationManager
import android.content.Context // Added import for Context
import kotlinx.coroutines.cancel // Added import for the cancel extension function
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext // Import for thread switching


// TimerService: A Foreground Service to run timers in the background.
// This service will eventually manage all active CountDownTimers.
class TimerService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var database: AppDatabase
    private lateinit var timerDao: TimerDao
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private lateinit var notificationManager: NotificationManager
    // Using SupervisorJob for serviceScope so children failures don't cancel parent
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // Map to hold active CountDownTimer instances by their ID
    private val activeCountDownTimers = ConcurrentHashMap<Long, CountDownTimer>()


    companion object {
        const val TAG = "TimerService"
        const val ACTION_START_TIMER_SERVICE = "com.catto.cookietimer.ACTION_START_TIMER_SERVICE"
        const val ACTION_STOP_TIMER_SERVICE = "com.catto.cookietimer.ACTION_STOP_TIMER_SERVICE"
        const val ACTION_START_TIMER = "com.catto.cookietimer.ACTION_START_TIMER"
        const val ACTION_STOP_TIMER = "com.catto.cookietimer.ACTION_STOP_TIMER"
        const val ACTION_RESET_TIMER = "com.catto.cookietimer.ACTION_RESET_TIMER"

        const val EXTRA_TIMER_ID = "extra_timer_id" // To pass timer ID with intents
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        database = AppDatabase.getDatabase(this) // Initialized database
        timerDao = database.timerDao() // Initialized timerDao
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound)?.also { it.isLooping = false }
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Log.d(TAG, "TimerService onCreate - Database, DAO, NotificationHelper, MediaPlayer, Vibrator, NotificationManager initialized.")

        // CRUCIAL FIX: Observe timers from DB within the service's lifecycle.
        // This Flow now correctly drives the state of activeCountDownTimers.
        // All timer start/stop/reset actions now only update the DB, and the DB Flow
        // then triggers the actual CountDownTimer management here.
        serviceScope.launch {
            timerDao.getAllTimers().collectLatest { loadedTimers ->
                Log.d(TAG, "Service: Timers loaded from DB: ${loadedTimers.size} timers. Reconciling internal state.")

                val runningTimersFromDb = loadedTimers.filter { it.isRunning }
                val runningTimerIdsFromDb = runningTimersFromDb.map { it.id }.toSet()

                // PHASE 1: Stop internal timers that are running but should NOT be running according to DB
                // This handles cases where a timer was stopped/reset/deleted from UI, or finished naturally.
                activeCountDownTimers.keys.forEach { activeId ->
                    if (activeId !in runningTimerIdsFromDb) {
                        stopCountdownForTimer(activeId) // Simply stop and remove from map. DB update handled elsewhere.
                        Log.d(TAG, "Service: Cancelled internal timer ${activeId} as it's no longer marked running in DB or was deleted.")
                    }
                }

                // PHASE 2: Start/resume timers that *should* be running according to DB but aren't internally
                // This handles cases where a timer was started/resumed from UI, or service restarted.
                runningTimersFromDb.forEach { timerFromDb ->
                    if (!activeCountDownTimers.containsKey(timerFromDb.id)) {
                        // Recalculate remaining time for accurate resumption (after potential app/service kill)
                        val elapsed = System.currentTimeMillis() - (timerFromDb.lastStartedTimestamp ?: System.currentTimeMillis())
                        val actualRemainingSeconds = (timerFromDb.initialDurationSeconds - (elapsed / 1000)).toInt().coerceAtLeast(0)

                        if (actualRemainingSeconds > 0) {
                            val resumedTimer = timerFromDb.copy(remainingTimeSeconds = actualRemainingSeconds)
                            startCountdownForTimer(resumedTimer) // Start countdown in service (will add to map)
                            Log.d(TAG, "Service: Resumed timer ${resumedTimer.name} (ID: ${resumedTimer.id}) from DB. Calculated remaining: ${resumedTimer.remainingTimeSeconds}s")
                        } else {
                            // If time ran out while service was down or in a stale state, mark it complete.
                            val completedTimer = timerFromDb.copy(remainingTimeSeconds = 0, isRunning = false, isCompleted = true)
                            timerDao.updateTimer(completedTimer) // Mark as completed in DB
                            notificationHelper.showNotification(
                                NotificationHelper.TIMER_ALARM_NOTIFICATION_ID + completedTimer.id.toInt(),
                                notificationHelper.buildTimerAlarmNotification(completedTimer.name, completedTimer.id)
                            )
                            Log.d(TAG, "Service: Timer ${completedTimer.name} (ID: ${completedTimer.id}) was already finished on service start (time ran out).")
                        }
                    }
                }
                updateForegroundNotificationContent() // Update notification after all reconciliation
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "TimerService onStartCommand: ${intent?.action}, Timer ID: ${intent?.getLongExtra(EXTRA_TIMER_ID, -1L)}")

        // CRUCIAL FIX: Call startForeground() unconditionally and immediately on almost any onStartCommand call.
        // This ensures the service doesn't crash due to not starting foreground in time.
        if (intent?.action != ACTION_STOP_TIMER_SERVICE) {
            val notificationBuilder = notificationHelper.buildRunningTimerNotification()
            startForeground(NotificationHelper.RUNNING_TIMER_NOTIFICATION_ID, notificationBuilder.build())
            Log.d(TAG, "TimerService ensuring foreground status on any non-stop command.")
        } else {
            Log.d(TAG, "TimerService received STOP_SERVICE action, will not call startForeground.")
        }

        when (intent?.action) {
            ACTION_START_TIMER_SERVICE -> {
                // This intent simply ensures the service is running in foreground.
                // The actual timer start/resume logic is driven by the Flow observer in onCreate.
                Log.d(TAG, "TimerService started by ACTION_START_TIMER_SERVICE (initial setup via Flow in onCreate).")
            }
            ACTION_STOP_TIMER_SERVICE -> {
                stopAllActiveTimers() // Stops all internal timers
                stopForeground(true) // Removes notification and stops service from foreground state
                stopSelf() // Stops the service itself
                Log.d(TAG, "TimerService explicitly stopped by ACTION_STOP_TIMER_SERVICE.")
            }
            // For START, STOP, RESET, we now ONLY update the database.
            // The database update will trigger the Flow in onCreate, which then reconciles internal timers.
            ACTION_START_TIMER -> {
                val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
                if (timerId != -1L) {
                    serviceScope.launch {
                        val timer = timerDao.getAllTimers().firstOrNull()?.find { it.id == timerId }
                        timer?.let {
                            val timeToStart = if (it.isCompleted) it.initialDurationSeconds else it.remainingTimeSeconds
                            val updatedTimer = it.copy(
                                remainingTimeSeconds = timeToStart,
                                isRunning = true,
                                isCompleted = false,
                                lastStartedTimestamp = System.currentTimeMillis()
                            )
                            timerDao.updateTimer(updatedTimer) // This DB update triggers the Flow -> startCountdownForTimer
                            Log.d(TAG, "Sent start command for timer ${it.name} (ID: ${it.id}) to DB.")
                        }
                    }
                }
            }
            ACTION_STOP_TIMER -> {
                val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
                if (timerId != -1L) {
                    serviceScope.launch {
                        val timer = timerDao.getAllTimers().firstOrNull()?.find { it.id == timerId }
                        timer?.let {
                            // We explicitly stop the internal timer first to ensure it halts immediately
                            stopCountdownForTimer(it.id)
                            val updatedTimer = it.copy(isRunning = false, lastStartedTimestamp = null)
                            timerDao.updateTimer(updatedTimer) // This DB update triggers the Flow -> UI update
                            Log.d(TAG, "Sent stop command for timer ${it.name} (ID: ${it.id}) to DB.")
                        }
                    }
                }
            }
            ACTION_RESET_TIMER -> {
                val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
                if (timerId != -1L) {
                    serviceScope.launch {
                        val timer = timerDao.getAllTimers().firstOrNull()?.find { it.id == timerId }
                        timer?.let {
                            // We explicitly stop the internal timer first to ensure it halts immediately
                            stopCountdownForTimer(it.id)
                            val updatedTimer = it.copy(remainingTimeSeconds = it.initialDurationSeconds, isRunning = false, isCompleted = false, lastStartedTimestamp = null)
                            timerDao.updateTimer(updatedTimer) // This DB update triggers the Flow -> UI update
                            Log.d(TAG, "Sent reset command for timer ${it.name} (ID: ${it.id}) to DB.")
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "TimerService onBind")
        return null
    }

    // This function starts a CountDownTimer on the Main thread.
    // It should *only* be called from the Flow observer in onCreate or internally when a timer needs to be started/resumed.
    private suspend fun startCountdownForTimer(timer: Timer) {
        // Ensure only one CountDownTimer runs per timer ID.
        // This is the primary point of control. Any previous timer for this ID is cancelled.
        activeCountDownTimers[timer.id]?.cancel()
        activeCountDownTimers.remove(timer.id) // Explicitly remove before re-adding


        val actualRemainingMillis = timer.remainingTimeSeconds * 1000L
        if (actualRemainingMillis <= 0) {
            Log.w(TAG, "Attempted to start timer ${timer.name} (ID: ${timer.id}) with non-positive remaining time. Marking as completed.")
            // No need to update DB here, as this function is called by the DB Flow or internal onFinish.
            notificationHelper.showNotification(
                NotificationHelper.TIMER_ALARM_NOTIFICATION_ID + timer.id.toInt(),
                notificationHelper.buildTimerAlarmNotification(timer.name, timer.id)
            )
            updateForegroundNotificationContent()
            return
        }

        withContext(Dispatchers.Main) { // CountDownTimer must be created on Main thread
            val countdown = object : CountDownTimer(actualRemainingMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    serviceScope.launch { // Update DB on IO dispatcher
                        val currentRemaining = (millisUntilFinished / 1000).toInt()
                        // Only update DB if the value actually changed to reduce DB writes
                        if (currentRemaining != timer.remainingTimeSeconds) { // Optimization to reduce DB writes
                            val updatedTimer = timer.copy(remainingTimeSeconds = currentRemaining)
                            timerDao.updateTimer(updatedTimer) // Update DB
                            // Log.d(TAG, "Timer ${timer.name} (ID:${timer.id}) ticking. Remaining: ${updatedTimer.remainingTimeSeconds}s. Actual: ${millisUntilFinished / 1000}s")
                        }
                    }
                }

                override fun onFinish() {
                    serviceScope.launch { // Update DB, alarm, notification on IO dispatcher
                        val updatedTimer = timer.copy(remainingTimeSeconds = 0, isRunning = false, isCompleted = true)
                        timerDao.updateTimer(updatedTimer)
                        activeCountDownTimers.remove(timer.id) // Remove from active map

                        playAlarmAndVibrate() // Play alarm
                        notificationHelper.showNotification(
                            NotificationHelper.TIMER_ALARM_NOTIFICATION_ID + timer.id.toInt(), // Unique ID per alarm
                            notificationHelper.buildTimerAlarmNotification(timer.name, timer.id)
                        )
                        Log.d(TAG, "Timer ${timer.name} (ID: ${timer.id}) finished.")

                        // Check if any other timers are running, if not, stop service
                        val anyRunning = timerDao.getAllTimers().firstOrNull()?.any { it.isRunning } ?: false
                        if (!anyRunning) {
                            stopSelf() // Stop service if no more timers running
                            Log.d(TAG, "No more timers running, stopping TimerService.")
                        }
                        updateForegroundNotificationContent()
                    }
                }
            }
            activeCountDownTimers[timer.id] = countdown
            countdown.start()
            Log.d(TAG, "Countdown started for Timer: ${timer.name} (ID: ${timer.id}) - Initial Remaining: ${timer.remainingTimeSeconds}s")
        }
    }

    // This function stops a CountDownTimer and removes it from the map.
    // It does NOT update the DB, as the DB change should come from onStartCommand's DB update or timer finish.
    private fun stopCountdownForTimer(timerId: Long) {
        activeCountDownTimers[timerId]?.cancel()
        activeCountDownTimers.remove(timerId)
        Log.d(TAG, "Countdown cancelled for Timer ID: $timerId")
        // No explicit updateForegroundNotificationContent() here; it's handled by the DB Flow reacting to DB change
    }

    // Called primarily by ACTION_STOP_TIMER_SERVICE to explicitly clean up all timers.
    private fun stopAllActiveTimers() {
        Log.d(TAG, "Stopping all active countdown timers...")
        activeCountDownTimers.values.forEach { it.cancel() }
        activeCountDownTimers.clear()
        Log.d(TAG, "All active countdown timers stopped.")
        serviceScope.launch { updateForegroundNotificationContent() } // Ensure notification reflects zero running timers
    }

    private fun playAlarmAndVibrate() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            seekTo(0)
            start()
        }
        vibrator?.apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrate(longArrayOf(0, 500, 200, 500), -1)
            }
        }
    }

    private fun updateForegroundNotificationContent() {
        serviceScope.launch { // Launch on IO to get data from DB
            val totalRunning = timerDao.getAllTimers().firstOrNull()?.count { it.isRunning } ?: 0
            val notificationBuilder = if (totalRunning > 0) {
                notificationHelper.buildRunningTimerNotification(totalRunning)
            } else {
                notificationHelper.buildRunningTimerNotification(0)
            }
            withContext(Dispatchers.Main) {
                notificationManager.notify(NotificationHelper.RUNNING_TIMER_NOTIFICATION_ID, notificationBuilder.build())
            }
        }
    }

    override fun onDestroy() {
        stopAllActiveTimers() // Ensure all timers are cancelled when service is destroyed
        serviceScope.cancel() // Cancel the coroutine scope to stop all ongoing coroutines
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        Log.d(TAG, "TimerService onDestroy")
        super.onDestroy()
    }
}
