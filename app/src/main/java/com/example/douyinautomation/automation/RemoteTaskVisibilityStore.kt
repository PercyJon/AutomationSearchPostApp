package com.example.douyinautomation.automation

import android.content.Context

/** Stores the optional remote-task dashboard visibility preference. */
object RemoteTaskVisibilityStore {
    private const val PREFS_NAME = "automation_ui_preferences"
    private const val KEY_SHOW_REMOTE_TASKS = "show_remote_tasks"

    /** A missing preference deliberately defaults to hidden for local-first use. */
    fun isEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_SHOW_REMOTE_TASKS, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_REMOTE_TASKS, enabled)
            .apply()
    }
}
