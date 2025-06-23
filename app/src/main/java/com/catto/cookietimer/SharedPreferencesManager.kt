// --- src/main/java/com/catto/cookietimer/SharedPreferencesManager.kt ---
package com.catto.cookietimer

import android.content.Context
import android.content.SharedPreferences

// Helper class to manage SharedPreferences for app settings.
class SharedPreferencesManager(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "CookieTimerPrefs"
        private const val KEY_TEMPERATURE_UNIT = "temperature_unit"
        private const val DEFAULT_TEMPERATURE_UNIT = "Celsius"
    }

    // Get the saved temperature unit (e.g., "Celsius", "Fahrenheit", "GasMark")
    fun getTemperatureUnit(): String {
        return preferences.getString(KEY_TEMPERATURE_UNIT, DEFAULT_TEMPERATURE_UNIT) ?: DEFAULT_TEMPERATURE_UNIT
    }

    // Set the temperature unit
    fun setTemperatureUnit(unit: String) {
        preferences.edit().putString(KEY_TEMPERATURE_UNIT, unit).apply()
    }
}
