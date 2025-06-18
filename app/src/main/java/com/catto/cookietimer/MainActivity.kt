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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit

// MainActivity: Handles the main UI, manages the list of timers, and interacts with the adapter.
class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var timerAdapter: TimerAdapter
    private val timers = mutableListOf<Timer>() // List to hold all timer data

    // MediaPlayer for the alarm sound
    private var mediaPlayer: MediaPlayer? = null
    // Vibrator for haptic feedback
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Set the main activity layout

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
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add New Timer")

        // Set up the layout for the dialog inputs
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20) // Add some padding
        }

        // Input for timer name
        val inputName = EditText(this).apply {
            hint = "Timer Name (e.g., Bake Cookies)"
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setSingleLine(true) // Prevent multi-line input
        }
        layout.addView(inputName)

        // Input for timer duration (minutes)
        val inputDuration = EditText(this).apply {
            hint = "Duration (minutes)"
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setText("1") // Default value
        }
        layout.addView(inputDuration)

        builder.setView(layout)

        // Set up the positive (Add) button
        builder.setPositiveButton("Add") { dialog, _ ->
            val name = inputName.text.toString().trim()
            val durationText = inputDuration.text.toString()
            val durationMinutes = durationText.toIntOrNull()

            if (name.isNotEmpty() && durationMinutes != null && durationMinutes > 0) {
                // Create and add the new timer
                val newTimer = Timer(
                    id = System.currentTimeMillis(), // Unique ID
                    name = name,
                    initialDurationSeconds = durationMinutes * 60,
                    remainingTimeSeconds = durationMinutes * 60,
                    isRunning = false,
                    isCompleted = false
                )
                timers.add(newTimer)
                timerAdapter.notifyItemInserted(timers.size - 1) // Notify adapter of new item
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a valid name and duration (min 1 minute).", Toast.LENGTH_LONG).show()
            }
        }

        // Set up the negative (Cancel) button
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show() // Display the dialog
    }

    // Starts a specific timer by its ID
    private fun startTimer(timerId: Long) {
        val index = timers.indexOfFirst { it.id == timerId }
        if (index != -1) {
            val timer = timers[index]
            if (!timer.isRunning) {
                // If the timer was completed, reset its remaining time before starting
                val timeToStart = if (timer.isCompleted) timer.initialDurationSeconds else timer.remainingTimeSeconds

                timer.countDownTimer = object : CountDownTimer(timeToStart * 1000L, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        timer.remainingTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished).toInt()
                        timerAdapter.notifyItemChanged(index) // Update UI for this timer
                    }

                    override fun onFinish() {
                        timer.remainingTimeSeconds = 0
                        timer.isRunning = false
                        timer.isCompleted = true
                        timer.countDownTimer = null // Clear reference
                        timerAdapter.notifyItemChanged(index) // Update UI for completion
                        playAlarmAndVibrate() // Trigger alarm
                    }
                }.start()
                timer.isRunning = true
                timer.isCompleted = false // Reset completed status when starting
                timerAdapter.notifyItemChanged(index) // Update UI
            }
        }
    }

    // Stops a specific timer by its ID
    private fun stopTimer(timerId: Long) {
        val index = timers.indexOfFirst { it.id == timerId }
        if (index != -1) {
            val timer = timers[index]
            if (timer.isRunning) {
                timer.countDownTimer?.cancel() // Cancel the countdown
                timer.isRunning = false
                timer.countDownTimer = null // Clear reference
                timerAdapter.notifyItemChanged(index) // Update UI
            }
        }
    }

    // Resets a specific timer by its ID
    private fun resetTimer(timerId: Long) {
        val index = timers.indexOfFirst { it.id == timerId }
        if (index != -1) {
            val timer = timers[index]
            stopTimer(timerId) // Stop if running
            timer.remainingTimeSeconds = timer.initialDurationSeconds
            timer.isCompleted = false
            timerAdapter.notifyItemChanged(index) // Update UI
        }
    }

    // Plays the alarm sound and vibrates the device
    private fun playAlarmAndVibrate() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop() // Stop any ongoing sound before playing again
            }
            seekTo(0) // Rewind to start
            start() // Play sound
        }

        vibrator?.apply {
            if (hasVibrator()) {
                val pattern = longArrayOf(0, 500, 200, 500) // Vibrate, pause, vibrate
                // For API level 26 (Oreo) and above
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrate(VibrationEffect.createWaveform(pattern, -1)) // -1 means don't repeat
                } else {
                    // Deprecated in API 26, but still works for older devices
                    @Suppress("DEPRECATION")
                    vibrate(pattern, -1) // -1 means don't repeat
                }
            }
        }
    }

    // Callback class for swipe-to-delete functionality
    inner class SwipeToDeleteCallback : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false // Not interested in drag & drop
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                // Show confirmation dialog before deleting
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete Timer")
                    .setMessage("Are you sure you want to delete this timer?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        val timerToDelete = timers[position]
                        stopTimer(timerToDelete.id) // Stop timer if running before deletion
                        timers.removeAt(position)
                        timerAdapter.notifyItemRemoved(position) // Notify adapter
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        // If canceled, revert the swipe animation
                        timerAdapter.notifyItemChanged(position)
                        dialog.cancel()
                    }
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop and release MediaPlayer resources
        mediaPlayer?.release()
        mediaPlayer = null

        // Cancel any ongoing vibrations
        vibrator?.cancel()

        // Cancel all active CountDownTimers to prevent leaks
        timers.forEach { it.countDownTimer?.cancel() }
    }
}

