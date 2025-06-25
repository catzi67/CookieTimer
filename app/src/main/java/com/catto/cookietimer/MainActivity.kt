// --- src/main/java/com/catto/cookietimer/MainActivity.kt ---
package com.catto.cookietimer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.util.Log
import com.google.android.material.snackbar.Snackbar

// MainActivity: Handles the main UI, manages the list of timers, and interacts with the adapter.
class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var timerAdapter: TimerAdapter
    // The list of timers is now managed by the ListAdapter, so no local list is needed.

    private lateinit var database: AppDatabase
    private lateinit var timerDao: TimerDao
    private lateinit var sharedPreferencesManager: SharedPreferencesManager

    companion object {
        private const val TAG = "CookieTimerApp"
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request notification permission on newer Android versions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_POST_NOTIFICATIONS)
            }
        }

        // Initialize components
        database = AppDatabase.getDatabase(this)
        timerDao = database.timerDao()
        sharedPreferencesManager = SharedPreferencesManager(this)

        // Setup RecyclerView and Adapter
        setupRecyclerView()

        // Observe timers from the database and submit them to the adapter
        CoroutineScope(Dispatchers.Main).launch {
            timerDao.getAllTimers().collectLatest { loadedTimers ->
                Log.d(TAG, "Timers loaded from DB: ${loadedTimers.size} timers.")
                timerAdapter.submitList(loadedTimers) // Use submitList for efficient updates

                // Ensure service is running if there are active timers when app opens
                if (loadedTimers.any { it.isRunning }) {
                    startTimerService()
                }
            }
        }

        // Set up button listeners
        findViewById<MaterialButton>(R.id.buttonAddTimer).setOnClickListener {
            showAddTimerDialog()
        }
        findViewById<MaterialButton>(R.id.buttonSettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewTimers)
        recyclerView.layoutManager = LinearLayoutManager(this)
        timerAdapter = TimerAdapter(
            onStartClick = { timerId -> startTimer(timerId) },
            onStopClick = { timerId -> stopTimer(timerId) },
            onResetClick = { timerId -> resetTimer(timerId) },
            currentTemperatureUnit = sharedPreferencesManager.getTemperatureUnit()
        )
        recyclerView.adapter = timerAdapter

        // Attach ItemTouchHelper to enable swipe-to-delete
        val itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback())
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onResume() {
        super.onResume()
        // Update the adapter with the latest temperature unit preference and redraw items
        timerAdapter.currentTemperatureUnit = sharedPreferencesManager.getTemperatureUnit()
        timerAdapter.notifyItemRangeChanged(0, timerAdapter.itemCount)
        Log.d(TAG, "MainActivity onResume: Updated adapter with temperature unit: ${sharedPreferencesManager.getTemperatureUnit()}")
    }

    private fun startTimerService() {
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START_TIMER_SERVICE
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.d(TAG, "Attempted to start TimerService.")
    }

    private fun showAddTimerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_timer, null)
        val inputName = dialogView.findViewById<EditText>(R.id.inputTimerName)
        val inputDuration = dialogView.findViewById<EditText>(R.id.inputTimerDuration)
        val inputTemperature = dialogView.findViewById<EditText>(R.id.inputTemperature)

        inputDuration.setText("1")

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_new_timer_dialog_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.add_button_text)) { dialog, _ ->
                val name = inputName.text.toString().trim()
                val durationMinutes = inputDuration.text.toString().toIntOrNull()
                val temperatureCelsius = inputTemperature.text.toString().trim().toDoubleOrNull()

                if (name.isNotEmpty() && durationMinutes != null && durationMinutes > 0) {
                    val newTimer = Timer(
                        name = name,
                        initialDurationSeconds = durationMinutes * 60,
                        remainingTimeSeconds = durationMinutes * 60,
                        temperatureCelsius = temperatureCelsius,
                        originalInputUnit = "Celsius"
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        timerDao.insertTimer(newTimer)
                        Log.d(TAG, "Timer inserted into DB.")
                        startTimerService()
                    }
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, getString(R.string.toast_invalid_input), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun sendTimerCommand(timerId: Long, action: String) {
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            this.action = action
            putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        }
        startService(serviceIntent)
        Log.d(TAG, "Sent $action command for Timer ID: $timerId to TimerService.")
    }

    private fun startTimer(timerId: Long) = sendTimerCommand(timerId, TimerService.ACTION_START_TIMER)
    private fun stopTimer(timerId: Long) = sendTimerCommand(timerId, TimerService.ACTION_STOP_TIMER)
    private fun resetTimer(timerId: Long) = sendTimerCommand(timerId, TimerService.ACTION_RESET_TIMER)

    inner class SwipeToDeleteCallback : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val timerToDelete = timerAdapter.currentList[position]

                // Delete from the database immediately. The Flow will update the UI.
                CoroutineScope(Dispatchers.IO).launch {
                    timerDao.deleteTimer(timerToDelete)
                    Log.d(TAG, "Timer with ID ${timerToDelete.id} deleted from DB.")
                }

                // Show a Snackbar with an Undo action
                Snackbar.make(recyclerView, "Timer deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        // If undo is clicked, re-insert the timer. The Flow will update the UI.
                        CoroutineScope(Dispatchers.IO).launch {
                            timerDao.insertTimer(timerToDelete)
                            Log.d(TAG, "Undo delete for timer with ID ${timerToDelete.id}.")
                        }
                    }
                    .show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied.")
                Toast.makeText(this, getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity onDestroy called.")
    }
}
