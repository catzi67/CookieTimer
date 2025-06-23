// --- src/main/java/com/catto/cookietimer/SettingsActivity.kt ---
package com.catto.cookietimer

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import android.util.Log // Import for logging

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferencesManager: SharedPreferencesManager
    private lateinit var radioGroupTemperatureUnit: RadioGroup
    private lateinit var radioCelsius: RadioButton
    private lateinit var radioFahrenheit: RadioButton
    private lateinit var radioGasMark: RadioButton

    companion object {
        private const val TAG = "SettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferencesManager = SharedPreferencesManager(this)

        radioGroupTemperatureUnit = findViewById(R.id.radioGroupTemperatureUnit)
        radioCelsius = findViewById(R.id.radioCelsius)
        radioFahrenheit = findViewById(R.id.radioFahrenheit)
        radioGasMark = findViewById(R.id.radioGasMark)

        // Set the currently saved temperature unit
        val currentUnit = sharedPreferencesManager.getTemperatureUnit()
        when (currentUnit) {
            "Celsius" -> radioCelsius.isChecked = true
            "Fahrenheit" -> radioFahrenheit.isChecked = true
            "GasMark" -> radioGasMark.isChecked = true
            else -> radioCelsius.isChecked = true // Default to Celsius if none saved
        }
        Log.d(TAG, "Current temperature unit loaded: $currentUnit")


        // Listen for changes in the RadioGroup selection
        radioGroupTemperatureUnit.setOnCheckedChangeListener { _, checkedId ->
            val selectedUnit = when (checkedId) {
                R.id.radioCelsius -> "Celsius"
                R.id.radioFahrenheit -> "Fahrenheit"
                R.id.radioGasMark -> "GasMark"
                else -> "Celsius" // Fallback
            }
            sharedPreferencesManager.setTemperatureUnit(selectedUnit)
            Log.d(TAG, "Temperature unit changed to: $selectedUnit")
            // Optionally, you might want to send a broadcast or update Main Activity if it's visible
            // For now, MainActivity will reload the preference when its adapter binds
        }
    }
}
