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

    private lateinit var database: AppDatabase
    private lateinit var timerDao: TimerDao
    private lateinit var sharedPreferencesManager: SharedPreferencesManager

    // Store the theme resource ID that was applied in onCreate
    private var appliedThemeResId: Int = 0

    companion object {
        private const val TAG = "CookieTimerApp"
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferencesManager = SharedPreferencesManager(this)

        // Determine and set the theme, then store it
        appliedThemeResId = when (sharedPreferencesManager.getTheme()) {
            SharedPreferencesManager.THEME_DARK -> R.style.Theme_CookieTimer_Dark
            SharedPreferencesManager.THEME_OVEN_GLOW -> R.style.Theme_CookieTimer_OvenGlow
            else -> R.style.Theme_CookieTimer
        }
        setTheme(appliedThemeResId)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_POST_NOTIFICATIONS)
            }
        }

        database = AppDatabase.getDatabase(this)
        timerDao = database.timerDao()

        setupRecyclerView()

        CoroutineScope(Dispatchers.Main).launch {
            timerDao.getAllTimers().collectLatest { loadedTimers ->
                Log.d(TAG, "Timers loaded from DB: ${loadedTimers.size} timers.")
                timerAdapter.submitList(loadedTimers)

                if (loadedTimers.any { it.isRunning }) {
                    startTimerService()
                }
            }
        }

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

        val itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback())
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onResume() {
        super.onResume()
        // Check if the saved theme preference is different from the one applied when the activity was created
        val savedTheme = sharedPreferencesManager.getTheme()
        val expectedThemeResId = when (savedTheme) {
            SharedPreferencesManager.THEME_DARK -> R.style.Theme_CookieTimer_Dark
            SharedPreferencesManager.THEME_OVEN_GLOW -> R.style.Theme_CookieTimer_OvenGlow
            else -> R.style.Theme_CookieTimer
        }

        if (appliedThemeResId != expectedThemeResId) {
            recreate()
            return // Avoid doing other work if we are recreating
        }

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
                val tempInput = inputTemperature.text.toString().trim().toDoubleOrNull()

                // Convert the input temperature to Celsius based on the current setting
                val temperatureInCelsius = tempInput?.let {
                    when (sharedPreferencesManager.getTemperatureUnit()) {
                        "Fahrenheit" -> (it - 32) * 5 / 9
                        "GasMark" -> convertGasMarkToCelsius(it)
                        else -> it // Assumed to be Celsius
                    }
                }

                if (name.isNotEmpty() && durationMinutes != null && durationMinutes > 0) {
                    val newTimer = Timer(
                        name = name,
                        initialDurationSeconds = durationMinutes * 60,
                        remainingTimeSeconds = durationMinutes * 60,
                        temperatureCelsius = temperatureInCelsius
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

    private fun convertGasMarkToCelsius(gasMark: Double): Double {
        return when {
            gasMark < 1 -> 121.0
            gasMark < 2 -> 135.0
            gasMark < 3 -> 149.0
            gasMark < 4 -> 163.0
            gasMark < 5 -> 177.0
            gasMark < 6 -> 191.0
            gasMark < 7 -> 204.0
            gasMark < 8 -> 218.0
            gasMark < 9 -> 232.0
            else -> 246.0
        }
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

                CoroutineScope(Dispatchers.IO).launch {
                    timerDao.deleteTimer(timerToDelete)
                    Log.d(TAG, "Timer with ID ${timerToDelete.id} deleted from DB.")
                }

                Snackbar.make(recyclerView, "Timer deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
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
