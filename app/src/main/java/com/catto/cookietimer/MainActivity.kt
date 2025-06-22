// --- src/main/java/com/catto/cookietimer/MainActivity.kt ---
package com.catto.cookietimer

import android.app.AlertDialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit
import com.catto.cookietimer.TimerDao
import com.catto.cookietimer.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import android.util.Log // Import for logging

// MainActivity: Handles the main UI, manages the list of timers, and interacts with the adapter.
class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var timerAdapter: TimerAdapter
    private val timers = mutableListOf<Timer>() // List to hold all timer data

    // Room Database and DAO instances
    private lateinit var database: AppDatabase
    private lateinit var timerDao: TimerDao

    // MediaPlayer for the alarm sound
    private var mediaPlayer: MediaPlayer? = null
    // Vibrator for haptic feedback
    private var vibrator: Vibrator? = null

    companion object {
        private const val TAG = "CookieTimerApp" // Tag for logging
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main) // Set the main activity layout

        // Initialize Room Database and DAO
        database = AppDatabase.getDatabase(this)
        timerDao = database.timerDao()
        Log.d(TAG, "Database and DAO initialized.")

        // Initialize MediaPlayer and Vibrator
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound) // Ensure you have alarm_sound.mp3 in res/raw
        vibrator = getSystemService(Vibrator::class.java)

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recyclerViewTimers)
        recyclerView.layoutManager = LinearLayoutManager(this) // Linear list layout

        // Initialize adapter with the list of timers and callbacks for actions
        timerAdapter = TimerAdapter(timers,
            onStartClick = { timerId -> startTimer(timerId) },
            onStopClick = { timerId -> stopTimer(timerId) },
            onResetClick = { timerId -> resetTimer(timerId) }
        )
        recyclerView.adapter = timerAdapter

        // Observe timers from the database
        // Use lifecycleScope or viewModelScope in a real app, but CoroutineScope(Dispatchers.Main) for simplicity here
        CoroutineScope(Dispatchers.Main).launch {
            timerDao.getAllTimers().collectLatest { loadedTimers ->
                Log.d(TAG, "Timers loaded from DB: ${loadedTimers.size} timers.")
                // Stop any running timers before updating the list
                timers.forEach { it.countDownTimer?.cancel() }
                timers.clear()
                timers.addAll(loadedTimers)
                timerAdapter.notifyDataSetChanged()
                // Re-start timers that were previously running (if needed, or re-think this logic)
                // For simplicity, timers are not automatically restarted here.
                // You would need a more sophisticated state management for running timers across app restarts.
            }
        }


        // Set up the "Add Timer" button click listener
        findViewById<MaterialButton>(R.id.buttonAddTimer).setOnClickListener {
            showAddTimerDialog() // Show dialog to add new timer
        }

        // Attach ItemTouchHelper for swipe-to-delete functionality
        val itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback())
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    // Function to show a dialog for adding a new timer
    private fun showAddTimerDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.add_new_timer_dialog_title))

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_timer, null)
        builder.setView(dialogView)

        val inputName = dialogView.findViewById<EditText>(R.id.inputTimerName)
        val inputDuration = dialogView.findViewById<EditText>(R.id.inputTimerDuration)
        val inputTemperature = dialogView.findViewById<EditText>(R.id.inputTemperature) // New: Temperature input

        inputDuration.setText("1")
        // No default for temperature, it's optional

        builder.setPositiveButton(getString(R.string.add_button_text)) { dialog, _ ->
            val name = inputName.text.toString().trim()
            val durationText = inputDuration.text.toString()
            val durationMinutes = durationText.toIntOrNull()
            val temperatureText = inputTemperature.text.toString().trim() // New: Get temperature text
            val temperatureCelsius: Double? = temperatureText.toDoubleOrNull() // New: Convert to Double, nullable

            if (name.isNotEmpty() && durationMinutes != null && durationMinutes > 0) {
                val newTimer = Timer(
                    name = name,
                    initialDurationSeconds = durationMinutes * 60,
                    remainingTimeSeconds = durationMinutes * 60,
                    isRunning = false,
                    isCompleted = false,
                    temperatureCelsius = temperatureCelsius, // New: Add temperature
                    originalInputUnit = "Celsius" // New: Assume Celsius for input unit initially
                )
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val newId = timerDao.insertTimer(newTimer)
                        Log.d(TAG, "Timer inserted into DB with ID: $newId")
                        // UI update handled by Flow observer
                    } catch (e: Exception) {
                        Log.e(TAG, "Error inserting timer into DB: ${e.message}", e)
                        // Show a user-friendly message if insertion fails (e.g., Toast)
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(this@MainActivity, "Failed to add timer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                dialog.dismiss()
            } else {
                Toast.makeText(this, getString(R.string.toast_invalid_input), Toast.LENGTH_LONG).show()
            }
        }

        builder.setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    // Starts a specific timer by its ID
    private fun startTimer(timerId: Long) {
        val index = timers.indexOfFirst { it.id == timerId }
        if (index != -1) {
            val timer = timers[index]
            if (!timer.isRunning) {
                val timeToStart = if (timer.isCompleted) timer.initialDurationSeconds else timer.remainingTimeSeconds

                val updatedTimer = timer.copy(
                    remainingTimeSeconds = timeToStart,
                    isRunning = true,
                    isCompleted = false
                )
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        timerDao.updateTimer(updatedTimer)
                        Log.d(TAG, "Timer with ID ${timer.id} updated to running state in DB.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating timer ${timer.id} in DB (start): ${e.message}", e)
                    }
                }

                timer.countDownTimer = object : CountDownTimer(timeToStart * 1000L, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        timer.remainingTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished).toInt()
                        timerAdapter.notifyItemChanged(index)
                    }

                    override fun onFinish() {
                        timer.remainingTimeSeconds = 0
                        timer.isRunning = false
                        timer.isCompleted = true
                        timer.countDownTimer = null
                        timerAdapter.notifyItemChanged(index)
                        playAlarmAndVibrate()

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                timerDao.updateTimer(timer.copy(isRunning = false, isCompleted = true, remainingTimeSeconds = 0))
                                Log.d(TAG, "Timer with ID ${timer.id} updated to completed state in DB.")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating timer ${timer.id} in DB (finish): ${e.message}", e)
                            }
                        }
                    }
                }.start()
            }
        }
    }

    // Stops a specific timer by its ID
    private fun stopTimer(timerId: Long) {
        val index = timers.indexOfFirst { it.id == timerId }
        if (index != -1) {
            val timer = timers[index]
            if (timer.isRunning) {
                timer.countDownTimer?.cancel()
                timer.isRunning = false
                timer.countDownTimer = null
                timerAdapter.notifyItemChanged(index)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        timerDao.updateTimer(timer.copy(isRunning = false))
                        Log.d(TAG, "Timer with ID ${timer.id} updated to stopped state in DB.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating timer ${timer.id} in DB (stop): ${e.message}", e)
                    }
                }
            }
        }
    }

    // Resets a specific timer by its ID
    private fun resetTimer(timerId: Long) {
        val index = timers.indexOfFirst { it.id == timerId }
        if (index != -1) {
            val timer = timers[index]
            stopTimer(timerId)
            timer.remainingTimeSeconds = timer.initialDurationSeconds
            timer.isCompleted = false
            timerAdapter.notifyItemChanged(index)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    timerDao.updateTimer(timer.copy(remainingTimeSeconds = timer.initialDurationSeconds, isCompleted = false))
                    Log.d(TAG, "Timer with ID ${timer.id} updated to reset state in DB.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating timer ${timer.id} in DB (reset): ${e.message}", e)
                }
            }
        }
    }

    // Plays the alarm sound and vibrates the device
    private fun playAlarmAndVibrate() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            seekTo(0)
            start()
        }

        vibrator?.apply {
            if (hasVibrator()) {
                val pattern = longArrayOf(0, 500, 200, 500)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrate(pattern, -1)
                }
            }
        }
    }

    // Callback class for swipe-to-delete functionality for RecyclerView items
    inner class SwipeToDeleteCallback : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(getString(R.string.delete_timer_dialog_title))
                    .setMessage(getString(R.string.delete_timer_dialog_message))
                    .setPositiveButton(getString(R.string.delete_button_text)) { dialog, _ ->
                        val timerToDelete = timers[position]
                        stopTimer(timerToDelete.id)

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                timerDao.deleteTimer(timerToDelete)
                                Log.d(TAG, "Timer with ID ${timerToDelete.id} deleted from DB.")
                                // UI update handled by Flow observer
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting timer ${timerToDelete.id} from DB: ${e.message}", e)
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(this@MainActivity, "Failed to delete timer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ ->
                        timerAdapter.notifyItemChanged(position)
                        dialog.cancel()
                    }
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        timers.forEach { it.countDownTimer?.cancel() }
        Log.d(TAG, "MainActivity onDestroy called.")
    }
}
