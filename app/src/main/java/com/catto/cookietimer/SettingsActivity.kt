// --- src/main/java/com/catto/cookietimer/SettingsActivity.kt ---
package com.catto.cookietimer

import android.os.Bundle
import android.view.MenuItem
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferencesManager: SharedPreferencesManager
    private lateinit var radioGroupTemperatureUnit: RadioGroup
    private lateinit var radioCelsius: RadioButton
    private lateinit var radioFahrenheit: RadioButton
    private lateinit var radioGasMark: RadioButton
    private lateinit var radioGroupTheme: RadioGroup
    private lateinit var radioThemeLight: RadioButton
    private lateinit var radioThemeDark: RadioButton
    private lateinit var radioThemeOvenGlow: RadioButton

    companion object {
        private const val TAG = "SettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferencesManager = SharedPreferencesManager(this)
        // Set the theme before setting the content view
        when (sharedPreferencesManager.getTheme()) {
            SharedPreferencesManager.THEME_DARK -> setTheme(R.style.Theme_CookieTimer_Dark)
            SharedPreferencesManager.THEME_OVEN_GLOW -> setTheme(R.style.Theme_CookieTimer_OvenGlow)
            else -> setTheme(R.style.Theme_CookieTimer)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Setup the toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Initialize views
        radioGroupTemperatureUnit = findViewById(R.id.radioGroupTemperatureUnit)
        radioCelsius = findViewById(R.id.radioCelsius)
        radioFahrenheit = findViewById(R.id.radioFahrenheit)
        radioGasMark = findViewById(R.id.radioGasMark)
        radioGroupTheme = findViewById(R.id.radioGroupTheme)
        radioThemeLight = findViewById(R.id.radioThemeLight)
        radioThemeDark = findViewById(R.id.radioThemeDark)
        radioThemeOvenGlow = findViewById(R.id.radioThemeOvenGlow)

        setupTemperatureUnitSelector()
        setupThemeSelector()
    }

    private fun setupTemperatureUnitSelector() {
        val currentUnit = sharedPreferencesManager.getTemperatureUnit()
        when (currentUnit) {
            "Celsius" -> radioCelsius.isChecked = true
            "Fahrenheit" -> radioFahrenheit.isChecked = true
            "GasMark" -> radioGasMark.isChecked = true
        }
        Log.d(TAG, "Current temperature unit loaded: $currentUnit")

        radioGroupTemperatureUnit.setOnCheckedChangeListener { _, checkedId ->
            val selectedUnit = when (checkedId) {
                R.id.radioCelsius -> "Celsius"
                R.id.radioFahrenheit -> "Fahrenheit"
                R.id.radioGasMark -> "GasMark"
                else -> "Celsius"
            }
            sharedPreferencesManager.setTemperatureUnit(selectedUnit)
            Log.d(TAG, "Temperature unit changed to: $selectedUnit")
        }
    }

    private fun setupThemeSelector() {
        val currentTheme = sharedPreferencesManager.getTheme()
        when (currentTheme) {
            SharedPreferencesManager.THEME_LIGHT -> radioThemeLight.isChecked = true
            SharedPreferencesManager.THEME_DARK -> radioThemeDark.isChecked = true
            SharedPreferencesManager.THEME_OVEN_GLOW -> radioThemeOvenGlow.isChecked = true
        }
        Log.d(TAG, "Current theme loaded: $currentTheme")

        radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val selectedTheme = when (checkedId) {
                R.id.radioThemeLight -> SharedPreferencesManager.THEME_LIGHT
                R.id.radioThemeDark -> SharedPreferencesManager.THEME_DARK
                R.id.radioThemeOvenGlow -> SharedPreferencesManager.THEME_OVEN_GLOW
                else -> SharedPreferencesManager.THEME_LIGHT
            }
            sharedPreferencesManager.setTheme(selectedTheme)
            Log.d(TAG, "Theme changed to: $selectedTheme")
            recreate() // Recreate the activity to apply the new theme
        }
    }

    // Handle toolbar item clicks
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle the Up button press
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
