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
import android.content.Intent // Import for Intent
import androidx.core.app.ActivityCompat // For requesting permissions
import com.catto.cookietimer.TimerService // Added import for TimerService
import com.catto.cookietimer.TimerAdapter // Added explicit import for TimerAdapter


// MainActivity: Handles the main UI, manages the list of timers, and interacts with the adapter.
class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var timerAdapter: TimerAdapter
    private val timers = mutableListOf<Timer>() // List to hold all timer data

    // Room Database and DAO instances
    private lateinit var database: AppDatabase
    private lateinit var timerDao: TimerDao

    // MediaPlayer and Vibrator are now managed by TimerService, no longer needed in MainActivity
    // private var mediaPlayer: MediaPlayer? = null
    // private var vibrator: Vibrator? = null

    private lateinit var sharedPreferencesManager: SharedPreferencesManager // New: SharedPreferences Manager

    companion object {
        private const val TAG = "CookieTimerApp" // Tag for logging
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001 // Request code for notification permission
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main) // Set the main activity layout

        // Request POST_NOTIFICATIONS permission at runtime if not granted (for API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_POST_NOTIFICATIONS)
            }
        }


        // Initialize Room Database and DAO
        database = AppDatabase.getDatabase(this)
        timerDao = database.timerDao()
        Log.d(TAG, "Database and DAO initialized.")

        // Initialize SharedPreferencesManager
        sharedPreferencesManager = SharedPreferencesManager(this)


        // Initialize MediaPlayer and Vibrator
        // mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound)
        // vibrator = getSystemService(Vibrator::class.java)

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recyclerViewTimers)
        recyclerView.layoutManager = LinearLayoutManager(this) // Linear list layout

        // Initialize adapter with the list of timers and callbacks for actions
        // Pass the current temperature unit from preferences to the adapter
        timerAdapter = TimerAdapter(timers,
            onStartClick = { timerId -> startTimer(timerId) },
            onStopClick = { timerId -> stopTimer(timerId) },
            onResetClick = { timerId -> resetTimer(timerId) },
            currentTemperatureUnit = sharedPreferencesManager.getTemperatureUnit() // New parameter
        )
        recyclerView.adapter = timerAdapter

        // Observe timers from the database
        CoroutineScope(Dispatchers.Main).launch {
            timerDao.getAllTimers().collectLatest { loadedTimers ->
                Log.d(TAG, "Timers loaded from DB: ${loadedTimers.size} timers.")
                // Stop any CountDownTimers associated with old UI objects
                timers.forEach { it.countDownTimer?.cancel() } // Cancel old timers associated with old objects
                timers.clear()
                timers.addAll(loadedTimers) // Update local list from DB
                timerAdapter.notifyDataSetChanged() // Notify UI of changes

                // Logic to start/stop the service based on loaded timers
                if (loadedTimers.any { it.isRunning }) {
                    startTimerService()
                } else {
                    stopTimerService()
                }
            }
        }


        // Set up the "Add Timer" button click listener
        findViewById<MaterialButton>(R.id.buttonAddTimer).setOnClickListener {
            showAddTimerDialog()
        }

        // Set up the Settings button click listener
        findViewById<MaterialButton>(R.id.buttonSettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Update the adapter with the latest temperature unit preference
        timerAdapter.currentTemperatureUnit = sharedPreferencesManager.getTemperatureUnit()
        timerAdapter.notifyDataSetChanged() // Refresh all items to apply new unit
        Log.d(TAG, "MainActivity onResume: Updated adapter with temperature unit: ${sharedPreferencesManager.getTemperatureUnit()}")
    }

    // New: Functions to start and stop the TimerService
    private fun startTimerService() {
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START_TIMER_SERVICE
        }
        // For Android O (API 26) and above, use startForegroundService
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.d(TAG, "Attempted to start TimerService.")
    }

    private fun stopTimerService() {
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP_TIMER_SERVICE
        }
        stopService(serviceIntent)
        Log.d(TAG, "Attempted to stop TimerService.")
    }


    // Function to show a dialog for adding a new timer
    private fun showAddTimerDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.add_new_timer_dialog_title))

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_timer, null)
        builder.setView(dialogView)

        val inputName = dialogView.findViewById<EditText>(R.id.inputTimerName)
        val inputDuration = dialogView.findViewById<EditText>(R.id.inputTimerDuration)
        val inputTemperature = dialogView.findViewById<EditText>(R.id.inputTemperature)

        inputDuration.setText("1")

        builder.setPositiveButton(getString(R.string.add_button_text)) { dialog, _ ->
            val name = inputName.text.toString().trim()
            val durationText = inputDuration.text.toString()
            val durationMinutes = durationText.toIntOrNull()
            val temperatureText = inputTemperature.text.toString().trim()
            val temperatureCelsius: Double? = temperatureText.toDoubleOrNull()

            if (name.isNotEmpty() && durationMinutes != null && durationMinutes > 0) {
                val newTimer = Timer(
                    name = name,
                    initialDurationSeconds = durationMinutes * 60,
                    remainingTimeSeconds = durationMinutes * 60,
                    isRunning = false,
                    isCompleted = false,
                    temperatureCelsius = temperatureCelsius,
                    originalInputUnit = "Celsius" // Assume Celsius for input unit initially
                )
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val newId = timerDao.insertTimer(newTimer)
                        Log.d(TAG, "Timer inserted into DB with ID: $newId")
                        // After inserting, start the service if it's not already running
                        // The service will then handle actual countdown initiation when it receives update from DB Flow
                        startTimerService()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error inserting timer into DB: ${e.message}", e)
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

    // Starts a specific timer by its ID. Now sends an Intent to the TimerService.
    private fun startTimer(timerId: Long) {
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START_TIMER
            putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.d(TAG, "Sent start intent for Timer ID: $timerId to TimerService.")
        // No direct CountDownTimer in MainActivity anymore for active timers
        // UI will update via DB Flow
    }

    // Stops a specific timer by its ID. Now sends an Intent to the TimerService.
    private fun stopTimer(timerId: Long) {
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP_TIMER
            putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        }
        // Use startService to send command to running service
        startService(serviceIntent)
        Log.d(TAG, "Sent stop intent for Timer ID: $timerId to TimerService.")
        // UI will update via DB Flow
    }

    // Resets a specific timer by its ID. Now sends an Intent to the TimerService.
    private fun resetTimer(timerId: Long) {
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_RESET_TIMER
            putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        }
        // Use startService to send command to running service
        startService(serviceIntent)
        Log.d(TAG, "Sent reset intent for Timer ID: $timerId to TimerService.")
        // UI will update via DB Flow
    }

    // playAlarmAndVibrate is now handled entirely within TimerService, so remove it from MainActivity.
    // private fun playAlarmAndVibrate() { ... }

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
                        // Instead of calling stopTimer directly, delete it from DB.
                        // The TimerService will get the update via Flow and stop its internal timer.
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                timerDao.deleteTimer(timerToDelete)
                                Log.d(TAG, "Timer with ID ${timerToDelete.id} deleted from DB.")
                                // The service will handle stopping itself if no more timers are running.
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
        // MediaPlayer and Vibrator are now in service, so no release here.
        // mediaPlayer?.release()
        // mediaPlayer = null
        // vibrator?.cancel()
        timers.forEach { it.countDownTimer?.cancel() } // Ensure all CountDownTimers are cancelled
        // Service's lifecycle is now independent of MainActivity's onDestroy, stop only if truly done
        // stopTimerService() // No longer unconditionally stop service on Activity onDestroy
        Log.d(TAG, "MainActivity onDestroy called.")
    }

    // Handle permission request results
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if ((grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted.")
                // You can optionally restart service or update UI if needed
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied.")
                Toast.makeText(this, getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }
}
