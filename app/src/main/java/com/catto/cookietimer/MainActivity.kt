// --- src/main/java/com/catto/cookietimer/MainActivity.kt ---
package com.catto.cookietimer

import android.app.AlertDialog
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.widget.EditText
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

        // The status bar color is now handled by the theme and the translucent
        // status bar in themes.xml, allowing the activity's background to show through.

        setContentView(R.layout.activity_main) // Set the main activity layout

        // Initialize MediaPlayer and Vibrator
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound) // Ensure you have alarm_sound.mp3 in res/raw
        // Corrected: Use the class-based getSystemService to avoid deprecated string constant
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
        // Set the title for the dialog. This title will appear at the top of the custom dialog view.
        // Referencing string resource for dialog title
        builder.setTitle(getString(R.string.add_new_timer_dialog_title))

        // Inflate a custom layout for the dialog content
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_timer, null)
        builder.setView(dialogView) // Set the custom inflated view as the dialog's content

        // Get references to the EditText fields from the inflated custom layout
        val inputName = dialogView.findViewById<EditText>(R.id.inputTimerName)
        val inputDuration = dialogView.findViewById<EditText>(R.id.inputTimerDuration)

        // Set default value for duration input
        inputDuration.setText("1")

        // Set up the positive (Add) button for the dialog
        builder.setPositiveButton(getString(R.string.add_button_text)) { dialog, _ ->
            val name = inputName.text.toString().trim() // Get timer name from input
            val durationText = inputDuration.text.toString() // Get duration text
            val durationMinutes = durationText.toIntOrNull() // Convert to integer

            // Validate inputs
            if (name.isNotEmpty() && durationMinutes != null && durationMinutes > 0) {
                // Create and add the new Timer object to the list
                val newTimer = Timer(
                    id = System.currentTimeMillis(), // Unique ID for the timer
                    name = name,
                    initialDurationSeconds = durationMinutes * 60, // Convert minutes to seconds
                    remainingTimeSeconds = durationMinutes * 60,
                    isRunning = false,
                    isCompleted = false
                )
                timers.add(newTimer) // Add to data list
                timerAdapter.notifyItemInserted(timers.size - 1) // Notify RecyclerView adapter of the new item
                dialog.dismiss() // Close the dialog
            } else {
                // Show a toast message if input is invalid
                Toast.makeText(this, getString(R.string.toast_invalid_input), Toast.LENGTH_LONG).show()
            }
        }

        // Set up the negative (Cancel) button for the dialog
        builder.setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ -> dialog.cancel() } // Dismiss the dialog

        builder.show() // Display the AlertDialog
    }

    // Starts a specific timer by its ID
    private fun startTimer(timerId: Long) {
        val index = timers.indexOfFirst { it.id == timerId } // Find the index of the timer
        if (index != -1) { // Ensure timer exists
            val timer = timers[index]
            if (!timer.isRunning) { // Only start if not already running
                // Determine the time to start from: initial duration if completed, else remaining time
                val timeToStart = if (timer.isCompleted) timer.initialDurationSeconds else timer.remainingTimeSeconds

                // Create and start a new CountDownTimer
                timer.countDownTimer = object : CountDownTimer(timeToStart * 1000L, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        // Update remaining time every second
                        timer.remainingTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished).toInt()
                        timerAdapter.notifyItemChanged(index) // Notify adapter to refresh this timer's view
                    }

                    override fun onFinish() {
                        // When timer finishes
                        timer.remainingTimeSeconds = 0 // Set to 0
                        timer.isRunning = false // Not running
                        timer.isCompleted = true // Mark as completed
                        timer.countDownTimer = null // Clear timer reference
                        timerAdapter.notifyItemChanged(index) // Notify adapter for final state update
                        playAlarmAndVibrate() // Trigger alarm and vibrate
                    }
                }.start()
                timer.isRunning = true // Set running status
                timer.isCompleted = false // Clear completed status if starting after completion
                timerAdapter.notifyItemChanged(index) // Notify adapter of start
            }
        }
    }

    // Stops a specific timer by its ID
    private fun stopTimer(timerId: Long) {
        val index = timers.indexOfFirst { it.id == timerId }
        if (index != -1) {
            val timer = timers[index]
            if (timer.isRunning) {
                timer.countDownTimer?.cancel() // Cancel the active countdown
                timer.isRunning = false // Update running status
                timer.countDownTimer = null // Clear timer reference
                timerAdapter.notifyItemChanged(index) // Notify adapter of stop
            }
        }
    }

    // Resets a specific timer by its ID
    private fun resetTimer(timerId: Long) {
        val index = timers.indexOfFirst { it.id == timerId }
        if (index != -1) {
            val timer = timers[index]
            stopTimer(timerId) // Ensure it's stopped before resetting
            timer.remainingTimeSeconds = timer.initialDurationSeconds // Reset to initial duration
            timer.isCompleted = false // Not completed
            timerAdapter.notifyItemChanged(index) // Notify adapter of reset
        }
    }

    // Plays the alarm sound and vibrates the device
    private fun playAlarmAndVibrate() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop() // Stop any current playback
            }
            seekTo(0) // Rewind to the beginning
            start() // Start playing the alarm sound
        }

        vibrator?.apply {
            if (hasVibrator()) { // Check if device has a vibrator
                val pattern = longArrayOf(0, 500, 200, 500) // Vibrate for 500ms, pause 200ms, vibrate 500ms
                // Use VibrationEffect for newer Android versions (API 26+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrate(VibrationEffect.createWaveform(pattern, -1)) // -1 means don't repeat pattern
                } else {
                    // Use deprecated vibrate for older devices
                    @Suppress("DEPRECATION")
                    vibrate(pattern, -1) // -1 means don't repeat pattern
                }
            }
        }
    }

    // Callback class for swipe-to-delete functionality for RecyclerView items
    inner class SwipeToDeleteCallback : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
        // Not interested in drag & drop, so return false
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        // Called when an item is swiped
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // Get the position of the swiped item within the adapter's data set
            // bindingAdapterPosition is safer during complex RecyclerView updates
            val position = viewHolder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) { // Ensure position is valid
                // Show a confirmation dialog before proceeding with deletion
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.delete_timer_dialog_title))
                    .setMessage(getString(R.string.delete_timer_dialog_message))
                    .setPositiveButton(getString(R.string.delete_button_text)) { dialog, _ ->
                        val timerToDelete = timers[position]
                        stopTimer(timerToDelete.id) // Stop the timer before removing it
                        timers.removeAt(position) // Remove from the data source
                        timerAdapter.notifyItemRemoved(position) // Notify the adapter about the removal
                        dialog.dismiss() // Dismiss the confirmation dialog
                    }
                    .setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ ->
                        // If deletion is canceled, tell the adapter to redraw the item at its original position
                        timerAdapter.notifyItemChanged(position)
                        dialog.cancel() // Dismiss the dialog
                    }
                    .show() // Show the confirmation dialog
            }
        }
    }

    // Called when the activity is being destroyed
    override fun onDestroy() {
        super.onDestroy()
        // Release MediaPlayer resources to prevent memory leaks
        mediaPlayer?.release()
        mediaPlayer = null

        // Cancel any ongoing vibrations
        vibrator?.cancel()

        // Cancel all active CountDownTimers to prevent memory leaks when the activity is destroyed
        timers.forEach { it.countDownTimer?.cancel() }
    }
}
