// --- src/main/java/com/catto/cookietimer/TimerAdapter.kt ---
package com.catto.cookietimer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.Locale
import java.util.concurrent.TimeUnit

// TimerAdapter: Binds Timer data to individual card views in the RecyclerView.
// Updated to use ListAdapter with payloads for efficient partial updates.
class TimerAdapter(
    private val onStartClick: (Long) -> Unit, // Callback for Start button
    private val onStopClick: (Long) -> Unit,  // Callback for Stop button
    private val onResetClick: (Long) -> Unit,  // Callback for Reset button
    var currentTemperatureUnit: String // Current temperature unit from preferences
) : ListAdapter<Timer, TimerAdapter.TimerViewHolder>(DiffCallback) {

    // ViewHolder: Holds the views for a single timer card
    class TimerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timerName: TextView = itemView.findViewById(R.id.textViewTimerName)
        val countdownText: TextView = itemView.findViewById(R.id.textViewCountdown)
        val buttonStart: MaterialButton = itemView.findViewById(R.id.buttonStart)
        val buttonStop: MaterialButton = itemView.findViewById(R.id.buttonStop)
        val buttonReset: MaterialButton = itemView.findViewById(R.id.buttonReset)
        val temperatureText: TextView = itemView.findViewById(R.id.textViewTemperature)
    }

    // Creates new ViewHolder instances (inflates the card layout)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timer_card, parent, false)
        return TimerViewHolder(view)
    }

    // This onBindViewHolder is for partial updates (payloads).
    // It's called when DiffUtil determines that only specific parts of the item have changed.
    override fun onBindViewHolder(holder: TimerViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            // If there are no payloads, this is a full update, so we call the standard onBindViewHolder.
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // If there are payloads, we handle the partial update.
            val bundle = payloads[0] as Bundle
            for (key in bundle.keySet()) {
                if (key == "PAYLOAD_TIME_UPDATE") {
                    // If only the time changed, just update the countdown text.
                    holder.countdownText.text = formatTime(bundle.getInt(key))
                }
            }
        }
    }

    // This onBindViewHolder is for full updates (when the whole item needs to be redrawn).
    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        val timer = getItem(position)

        // Bind all data for a full redraw
        holder.timerName.text = timer.name
        holder.countdownText.text = formatTime(timer.remainingTimeSeconds)

        timer.temperatureCelsius?.let { tempCelsius ->
            val (convertedTemp, unitSymbol) = convertTemperature(tempCelsius, currentTemperatureUnit)
            holder.temperatureText.text = holder.itemView.context.getString(R.string.temperature_display_format, convertedTemp.toInt(), unitSymbol)
            holder.temperatureText.visibility = View.VISIBLE
        } ?: run {
            holder.temperatureText.visibility = View.GONE
        }

        holder.buttonStart.isEnabled = !timer.isRunning && !timer.isCompleted
        holder.buttonStop.isEnabled = timer.isRunning
        holder.buttonReset.isEnabled = true

        if (timer.isCompleted) {
            // Use string resource to fix hardcoded string warning
            holder.countdownText.text = holder.itemView.context.getString(R.string.timer_completed_text)
            holder.countdownText.setTextColor(holder.itemView.context.getColor(R.color.red_700))
        } else {
            holder.countdownText.setTextColor(holder.itemView.context.getColor(R.color.blue_600))
        }

        holder.buttonStart.setOnClickListener { onStartClick(timer.id) }
        holder.buttonStop.setOnClickListener { onStopClick(timer.id) }
        holder.buttonReset.setOnClickListener { onResetClick(timer.id) }
    }

    private fun formatTime(totalSeconds: Int): String {
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds.toLong())
        val seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes).toInt()
        // Specify locale to fix warning
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun convertTemperature(tempCelsius: Double, targetUnit: String): Pair<Double, String> {
        return when (targetUnit) {
            "Fahrenheit" -> ((tempCelsius * 9 / 5) + 32) to "F"
            "GasMark" -> convertCelsiusToGasMark(tempCelsius) to "GM"
            else -> tempCelsius to "C"
        }
    }

    private fun convertCelsiusToGasMark(tempCelsius: Double): Double {
        return when {
            tempCelsius < 135 -> 0.25; tempCelsius < 150 -> 1.0; tempCelsius < 165 -> 2.0
            tempCelsius < 175 -> 3.0; tempCelsius < 190 -> 4.0; tempCelsius < 200 -> 5.0
            tempCelsius < 220 -> 6.0; tempCelsius < 230 -> 7.0; tempCelsius < 240 -> 8.0
            tempCelsius < 260 -> 9.0; else -> 10.0
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Timer>() {
            override fun areItemsTheSame(oldItem: Timer, newItem: Timer): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Timer, newItem: Timer): Boolean {
                // This now checks all fields. If false, getChangePayload will be called.
                return oldItem == newItem
            }

            override fun getChangePayload(oldItem: Timer, newItem: Timer): Any? {
                // This is called if areContentsTheSame is false.
                // We check if only the time has changed and create a "payload" for it.
                val diffBundle = Bundle()
                if (newItem.remainingTimeSeconds != oldItem.remainingTimeSeconds) {
                    diffBundle.putInt("PAYLOAD_TIME_UPDATE", newItem.remainingTimeSeconds)
                }

                // If any other property changed, we don't add to the bundle,
                // which will trigger a full re-bind. This part is implicitly handled
                // by returning null if the bundle is empty.

                return if (diffBundle.size() == 0) null else diffBundle
            }
        }
    }
}
