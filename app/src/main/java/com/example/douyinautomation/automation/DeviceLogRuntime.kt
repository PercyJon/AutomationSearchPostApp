package com.example.douyinautomation.automation

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class PreferencesDeviceLogSeqStore(
    private val preferences: SharedPreferences,
) : DeviceLogSeqStore {
    @Synchronized
    override fun nextSeq(): Long {
        val next = preferences.getLong(NEXT_SEQ_KEY, 1L).coerceAtLeast(1L)
        preferences.edit().putLong(NEXT_SEQ_KEY, next + 1L).apply()
        return next
    }

    @Synchronized
    override fun lastAckedSeq(): Long = preferences.getLong(ACKED_SEQ_KEY, 0L)

    @Synchronized
    override fun setAckedSeq(seq: Long) {
        val current = lastAckedSeq()
        if (seq > current) {
            preferences.edit().putLong(ACKED_SEQ_KEY, seq).apply()
        }
    }

    companion object {
        const val PREFERENCES_NAME = "device_runtime_logs"
        private const val NEXT_SEQ_KEY = "next_seq"
        private const val ACKED_SEQ_KEY = "acked_seq"
    }
}

object DeviceLogRuntime {
    @Volatile
    var store: DeviceLogStore? = null
        private set

    fun initialize(context: Context): DeviceLogStore {
        store?.let { return it }
        synchronized(this) {
            store?.let { return it }
            val app = context.applicationContext
            val directory = File(File(app.filesDir, "diagnostics"), "runtime-logs")
            directory.mkdirs()
            val created = DeviceLogStore(
                directory = directory,
                seqStore = PreferencesDeviceLogSeqStore(
                    app.getSharedPreferences(PreferencesDeviceLogSeqStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
                ),
            )
            store = created
            return created
        }
    }

    fun append(entry: DiagnosticEntry) {
        store?.append(entry)
    }
}
