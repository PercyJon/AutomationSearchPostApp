package com.example.douyinautomation.automation

import com.example.douyinautomation.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DeviceLogUploader {
    private val mutex = Mutex()
    private var periodicJob: Job? = null

    fun start(scope: CoroutineScope) {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                flush()
                delay(UPLOAD_INTERVAL_MILLIS)
            }
        }
    }

    fun stop() {
        periodicJob?.cancel()
        periodicJob = null
    }

    fun flushAsync(scope: CoroutineScope) {
        scope.launch { flush() }
    }

    suspend fun flush() {
        mutex.withLock {
            val store = DeviceLogRuntime.store ?: return
            val config = AuthStore.currentConfig()?.takeIf(AuthConfig::isUsable) ?: return
            while (true) {
                val batch = store.pendingBatch()
                if (batch.isEmpty()) return
                val acked = runCatching {
                    AutomationHttpClient(config).uploadDeviceLogs(
                        appVersion = BuildConfig.VERSION_NAME,
                        entries = batch,
                    )
                }.getOrNull() ?: return
                if (acked < 1L) return
                store.ack(acked)
                if (acked < batch.last().seq) return
            }
        }
    }

    private const val UPLOAD_INTERVAL_MILLIS = 2 * 60 * 1000L
}
