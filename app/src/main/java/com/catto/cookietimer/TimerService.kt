// --- src/main/java/com/catto/cookietimer/TimerService.kt ---
package com.catto.cookietimer

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

// TimerService: A Foreground Service to run timers in the background.
class TimerService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var database: AppDatabase
    private lateinit var timerDao: TimerDao
    private var vibrator: Vibrator? = null
    // MediaPlayer is now managed per-alarm, not as a long-lived service property.
    // private var mediaPlayer: MediaPlayer? = null

    private lateinit var notificationManager: NotificationManager
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
        database = AppDatabase.getDatabase(this)
        timerDao = database.timerDao()
        vibrator = getSystemService(Vibrator::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)
        Log.d(TAG, "TimerService onCreate - Initialized.")

        serviceScope.launch {
            timerDao.getAllTimers().collectLatest { loadedTimers ->
                Log.d(TAG, "Service: Timers loaded from DB: ${loadedTimers.size} timers. Reconciling internal state.")

                val runningTimersFromDb = loadedTimers.filter { it.isRunning }
                val runningTimerIdsFromDb = runningTimersFromDb.map { it.id }.toSet()

                activeCountDownTimers.keys.forEach { activeId ->
                    if (activeId !in runningTimerIdsFromDb) {
                        stopCountdownForTimer(activeId)
                        Log.d(TAG, "Service: Cancelled internal timer $activeId as it's no longer running in DB.")
                    }
                }

                runningTimersFromDb.forEach { timerFromDb ->
                    if (!activeCountDownTimers.containsKey(timerFromDb.id)) {
                        if (timerFromDb.remainingTimeSeconds > 0) {
                            startCountdownForTimer(timerFromDb)
                            Log.d(TAG, "Service: Resumed timer ${timerFromDb.name} (ID: ${timerFromDb.id}) from DB. Remaining: ${timerFromDb.remainingTimeSeconds}s")
                        } else {
                            val completedTimer = timerFromDb.copy(remainingTimeSeconds = 0, isRunning = false, isCompleted = true)
                            timerDao.updateTimer(completedTimer)
                            notificationHelper.showNotification(
                                NotificationHelper.TIMER_ALARM_NOTIFICATION_ID + completedTimer.id.toInt(),
                                notificationHelper.buildTimerAlarmNotification(completedTimer.name, completedTimer.id)
                            )
                            Log.d(TAG, "Service: Timer ${completedTimer.name} (ID: ${completedTimer.id}) was already finished.")
                        }
                    }
                }
                updateForegroundNotificationContent()

                if (loadedTimers.none { it.isRunning }) {
                    Log.d(TAG, "No running timers in DB after reconciliation. Stopping service.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "TimerService onStartCommand: ${intent?.action}, Timer ID: ${intent?.getLongExtra(EXTRA_TIMER_ID, -1L)}")

        if (intent?.action != ACTION_STOP_TIMER_SERVICE) {
            val notificationBuilder = notificationHelper.buildRunningTimerNotification()
            startForeground(NotificationHelper.RUNNING_TIMER_NOTIFICATION_ID, notificationBuilder.build())
        } else {
            Log.d(TAG, "TimerService received STOP_SERVICE action, will not call startForeground.")
        }

        when (intent?.action) {
            ACTION_START_TIMER_SERVICE -> {
                Log.d(TAG, "TimerService started by ACTION_START_TIMER_SERVICE.")
            }
            ACTION_STOP_TIMER_SERVICE -> {
                stopAllActiveTimers()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "TimerService explicitly stopped.")
            }
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
                            timerDao.updateTimer(updatedTimer)
                            Log.d(TAG, "Sent start command for timer ${it.name} (ID: ${it.id}) to DB.")
                        }
                    }
                }
            }
            ACTION_STOP_TIMER -> {
                val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
                if (timerId != -1L) {
                    serviceScope.launch {
                        timerDao.getAllTimers().firstOrNull()?.find { it.id == timerId }?.let {
                            stopCountdownForTimer(it.id)
                            val updatedTimer = it.copy(isRunning = false, lastStartedTimestamp = null)
                            timerDao.updateTimer(updatedTimer)
                            Log.d(TAG, "Sent stop command for timer ${it.name} (ID: ${it.id}) to DB.")
                        }
                    }
                }
            }
            ACTION_RESET_TIMER -> {
                val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
                if (timerId != -1L) {
                    serviceScope.launch {
                        timerDao.getAllTimers().firstOrNull()?.find { it.id == timerId }?.let {
                            stopCountdownForTimer(it.id)
                            val updatedTimer = it.copy(
                                remainingTimeSeconds = it.initialDurationSeconds,
                                isRunning = false,
                                isCompleted = false,
                                lastStartedTimestamp = null
                            )
                            timerDao.updateTimer(updatedTimer)
                            Log.d(TAG, "Sent reset command for timer ${it.name} (ID: ${it.id}) to DB.")
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun startCountdownForTimer(timer: Timer) {
        activeCountDownTimers[timer.id]?.cancel()
        activeCountDownTimers.remove(timer.id)

        val actualRemainingMillis = timer.remainingTimeSeconds * 1000L
        if (actualRemainingMillis <= 0) {
            Log.w(TAG, "Attempted to start timer ${timer.name} (ID: ${timer.id}) with non-positive remaining time.")
            updateForegroundNotificationContent()
            return
        }

        withContext(Dispatchers.Main) {
            val countdown = object : CountDownTimer(actualRemainingMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    serviceScope.launch {
                        val currentRemaining = (millisUntilFinished / 1000).toInt()
                        if (currentRemaining != timer.remainingTimeSeconds) {
                            val updatedTimer = timer.copy(remainingTimeSeconds = currentRemaining)
                            timerDao.updateTimer(updatedTimer)
                        }
                    }
                }

                override fun onFinish() {
                    serviceScope.launch {
                        Log.d(TAG, "Timer ${timer.name} (ID: ${timer.id}) finished.")

                        notificationHelper.showNotification(
                            NotificationHelper.TIMER_ALARM_NOTIFICATION_ID + timer.id.toInt(),
                            notificationHelper.buildTimerAlarmNotification(timer.name, timer.id)
                        )
                        activeCountDownTimers.remove(timer.id)

                        // Play sounds and wait for completion
                        playVibration()
                        playAlarmSoundAndWait()

                        // AFTER sound is done, update the DB. This will trigger the service stop check.
                        Log.d(TAG, "Alarm finished. Updating DB for timer ${timer.name}")
                        val updatedTimer = timer.copy(remainingTimeSeconds = 0, isRunning = false, isCompleted = true)
                        timerDao.updateTimer(updatedTimer)
                    }
                }
            }
            activeCountDownTimers[timer.id] = countdown
            countdown.start()
            Log.d(TAG, "Countdown started for Timer: ${timer.name} (ID: ${timer.id}) - Remaining: ${timer.remainingTimeSeconds}s")
        }
    }

    private fun stopCountdownForTimer(timerId: Long) {
        activeCountDownTimers[timerId]?.cancel()
        activeCountDownTimers.remove(timerId)
        Log.d(TAG, "Countdown cancelled for Timer ID: $timerId")
    }

    private fun stopAllActiveTimers() {
        Log.d(TAG, "Stopping all active countdown timers...")
        activeCountDownTimers.values.forEach { it.cancel() }
        activeCountDownTimers.clear()
        serviceScope.launch { updateForegroundNotificationContent() }
    }

    private fun playVibration() {
        vibrator?.apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrate(longArrayOf(0, 500, 200, 500), -1)
            }
        }
    }

    private suspend fun playAlarmSoundAndWait() = suspendCancellableCoroutine<Unit> { continuation ->
        val mediaPlayer = MediaPlayer.create(this@TimerService, R.raw.alarm_sound)?.apply {
            isLooping = false
            setOnCompletionListener { mp ->
                Log.d(TAG, "MediaPlayer completed.")
                mp.release()
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
            setOnErrorListener { _, _, _ ->
                Log.e(TAG, "MediaPlayer error.")
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
                true // Error was handled
            }
        }

        if (mediaPlayer == null) {
            Log.e(TAG, "MediaPlayer failed to create.")
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            mediaPlayer.release()
        }

        try {
            mediaPlayer.start()
            Log.d(TAG, "MediaPlayer started.")
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer start failed.", e)
            mediaPlayer.release()
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
    }

    private fun updateForegroundNotificationContent() {
        serviceScope.launch {
            val totalRunning = timerDao.getAllTimers().firstOrNull()?.count { it.isRunning } ?: 0
            val notificationBuilder = notificationHelper.buildRunningTimerNotification(totalRunning)
            withContext(Dispatchers.Main) {
                notificationManager.notify(NotificationHelper.RUNNING_TIMER_NOTIFICATION_ID, notificationBuilder.build())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllActiveTimers()
        serviceScope.cancel()
        // No longer need to release the service-level media player
        vibrator?.cancel()
        Log.d(TAG, "TimerService onDestroy")
    }
}
