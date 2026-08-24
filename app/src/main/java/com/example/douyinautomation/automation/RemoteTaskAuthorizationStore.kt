package com.example.douyinautomation.automation

import android.content.Context

/** Operator-managed local allow-list for remote task IDs. Local tasks never use this store. */
object RemoteTaskAuthorizationStore {
    private const val PREFERENCES_NAME = "automation_remote_task_authorization"
    private const val AUTHORIZED_TASK_IDS_KEY = "authorized_task_ids"

    fun loadRaw(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getString(AUTHORIZED_TASK_IDS_KEY, "")
        .orEmpty()

    fun save(context: Context, raw: String): Set<Long> {
        val ids = parse(raw)
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(AUTHORIZED_TASK_IDS_KEY, ids.sorted().joinToString(","))
            .apply()
        return ids
    }

    fun authorizedTaskIds(context: Context): Set<Long> = parse(loadRaw(context))

    fun isAuthorized(context: Context, taskId: Long): Boolean = taskId in authorizedTaskIds(context)

    fun parse(raw: String): Set<Long> = raw
        .split(',', '，', '\n', '\r', ' ', '\t')
        .mapNotNull { value -> value.trim().toLongOrNull()?.takeIf { it > 0L } }
        .toSet()
}
