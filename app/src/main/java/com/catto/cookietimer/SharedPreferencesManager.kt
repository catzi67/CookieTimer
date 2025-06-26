// --- src/main/java/com/catto/cookietimer/SharedPreferencesManager.kt ---
package com.catto.cookietimer

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

// Helper class to manage SharedPreferences for app settings.
class SharedPreferencesManager(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "CookieTimerPrefs"
        private const val KEY_TEMPERATURE_UNIT = "temperature_unit"
        private const val DEFAULT_TEMPERATURE_UNIT = "Celsius"
        private const val KEY_THEME = "theme_setting"
        const val THEME_LIGHT = "Light"
        const val THEME_DARK = "Dark"
        const val THEME_OVEN_GLOW = "OvenGlow"
    }

    // Get the saved temperature unit (e.g., "Celsius", "Fahrenheit", "GasMark")
    fun getTemperatureUnit(): String {
        return preferences.getString(KEY_TEMPERATURE_UNIT, DEFAULT_TEMPERATURE_UNIT) ?: DEFAULT_TEMPERATURE_UNIT
    }

    // Set the temperature unit using KTX extension function
    fun setTemperatureUnit(unit: String) {
        preferences.edit {
            putString(KEY_TEMPERATURE_UNIT, unit)
        }
    }

    // Get the saved theme setting
    fun getTheme(): String {
        return preferences.getString(KEY_THEME, THEME_LIGHT) ?: THEME_LIGHT
    }

    // Set the theme setting using KTX extension function
    fun setTheme(theme: String) {
        preferences.edit {
            putString(KEY_THEME, theme)
        }
        applyTheme(theme)
    }

    // Apply the selected theme
    fun applyTheme(theme: String) {
        when (theme) {
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_OVEN_GLOW -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) // Handled by activity theme
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
