package com.catto.cookietimer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit

// TimerAdapter: Binds Timer data to individual card views in the RecyclerView.
class TimerAdapter(
    private val timers: List<Timer>,
    private val onStartClick: (Long) -> Unit, // Callback for Start button
    private val onStopClick: (Long) -> Unit,  // Callback for Stop button
    private val onResetClick: (Long) -> Unit  // Callback for Reset button
) : RecyclerView.Adapter<TimerAdapter.TimerViewHolder>() {

    // ViewHolder: Holds the views for a single timer card
    class TimerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timerName: TextView = itemView.findViewById(R.id.textViewTimerName)
        val countdownText: TextView = itemView.findViewById(R.id.textViewCountdown)
        val buttonStart: MaterialButton = itemView.findViewById(R.id.buttonStart)
        val buttonStop: MaterialButton = itemView.findViewById(R.id.buttonStop)
        val buttonReset: MaterialButton = itemView.findViewById(R.id.buttonReset)
    }

    // Creates new ViewHolder instances (inflates the card layout)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timer_card, parent, false) // Inflate the timer card layout
        return TimerViewHolder(view)
    }

    // Returns the total number of items in the list
    override fun getItemCount(): Int = timers.size

    // Binds data from a Timer object to the views in a ViewHolder
    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        val timer = timers[position]

        holder.timerName.text = timer.name
        holder.countdownText.text = formatTime(timer.remainingTimeSeconds)

        // Set button states based on timer status
        holder.buttonStart.isEnabled = !timer.isRunning && !timer.isCompleted
        holder.buttonStop.isEnabled = timer.isRunning
        holder.buttonReset.isEnabled = true // Always enabled to allow resetting even if stopped/completed

        // Update countdown text color if completed
        if (timer.isCompleted) {
            holder.countdownText.text = "TIME'S UP!"
            holder.countdownText.setTextColor(holder.itemView.context.getColor(R.color.red_700)) // Use a color from colors.xml
        } else {
            holder.countdownText.setTextColor(holder.itemView.context.getColor(R.color.blue_600)) // Restore default color
        }

        // Set click listeners for the buttons
        holder.buttonStart.setOnClickListener { onStartClick(timer.id) }
        holder.buttonStop.setOnClickListener { onStopClick(timer.id) }
        holder.buttonReset.setOnClickListener { onResetClick(timer.id) }
    }

    // Helper function to format time (seconds to MM:SS)
    private fun formatTime(totalSeconds: Int): String {
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds.toLong())
        val seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes).toInt()
        return String.format("%02d:%02d", minutes, seconds)
    }
}
