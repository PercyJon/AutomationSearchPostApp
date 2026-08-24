package com.example.douyinautomation.automation

import android.content.Context

/**
 * Persists the optional operator-selected global action gap. The accessibility service reads it
 * for each new action slot, so a saved change takes effect without rebinding the service.
 */
object AutomationActionIntervalSettingsStore {
    private const val PREFERENCES_NAME = "automation_ui_preferences"
    private const val ACTION_INTERVAL_MILLIS_KEY = "action_interval_millis"

    fun loadRaw(context: Context): String {
        val raw = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(ACTION_INTERVAL_MILLIS_KEY, "")
            .orEmpty()
        return raw.takeIf { AutomationActionIntervalPolicy.validationError(it) == null }.orEmpty()
    }

    /** Returns 0ms when the setting is blank, missing, or invalid. */
    fun configuredIntervalMillis(context: Context): Long =
        AutomationActionIntervalPolicy.configuredIntervalMillisOrNull(loadRaw(context))
            ?: AutomationActionIntervalPolicy.DISABLED_INTERVAL_MILLIS

    /** Returns a validation error instead of writing an invalid value. */
    fun save(context: Context, raw: String): String? {
        val normalized = raw.trim()
        val validationError = AutomationActionIntervalPolicy.validationError(normalized)
        if (validationError != null) return validationError
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ACTION_INTERVAL_MILLIS_KEY, normalized)
            .apply()
        return null
    }
}
