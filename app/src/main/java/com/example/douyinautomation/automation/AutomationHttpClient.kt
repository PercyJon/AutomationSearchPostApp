package com.example.douyinautomation.automation

import android.os.Build
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A redacted network failure that can safely be shown in the diagnostics UI. */
class AutomationGatewayException(
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

/**
 * Small standard-library HTTP client for the mobile automation API.
 *
 * The app intentionally has no general-purpose HTTP dependency in M3.  This client centralizes
 * the Bearer header, response envelope validation, timeouts, endpoint normalization and JSON
 * decoding.  It never writes the token or response body to logs.
 */
class AutomationHttpClient(
    private val config: AuthConfig,
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) : HeartbeatGateway, SearchPresetRemoteSource, AutomationTaskGateway {

    override suspend fun verify(request: HeartbeatRequest): HeartbeatResponse = withContext(Dispatchers.IO) {
        val payload = execute(
            path = "/automation/mobile/heartbeat",
            method = "POST",
            body = JSONObject().apply {
                put("device_id_hash", request.deviceIdHash)
                put("app_version", request.appVersion)
                put("platform", request.platform)
                put("package_name", PACKAGE_NAME)
                put("protocol_version", PROTOCOL_VERSION)
                put("device_model", Build.MODEL)
                put("os_version", Build.VERSION.RELEASE)
            },
        ).asObject()
        HeartbeatResponse(
            accepted = payload.optBoolean("accepted", false),
            message = payload.optString("message", "授权服务未返回说明"),
            nextCheckAfterMillis = payload.optLong(
                "next_check_after_seconds",
                HeartbeatResponse.DEFAULT_HEARTBEAT_INTERVAL_MILLIS / 1_000L,
            ).coerceAtLeast(60L) * 1_000L,
        )
    }

    override suspend fun fetchCatalog(): SearchPresetCatalog = withContext(Dispatchers.IO) {
        val payload = execute("/automation/mobile/search-presets", "GET").asObject()
        val items = payload.optJSONArray("items").toSearchPresets()
        require(items.isNotEmpty()) { "搜索预设目录为空" }
        SearchPresetCatalog(
            version = payload.optString("version", "0"),
            items = items,
            updatedAtMillis = parseTimestamp(payload.optNullableString("updated_at"))
                ?: System.currentTimeMillis(),
            source = SearchPresetCatalog.Source.REMOTE,
        )
    }

    suspend fun fetchRegionCatalog(): RegionCatalog = withContext(Dispatchers.IO) {
        val payload = execute("/automation/mobile/regions", "GET").asObject()
        RegionCatalog(
            version = payload.optString("version", "0"),
            items = payload.optJSONArray("items").toRegions(),
            updatedAtMillis = parseTimestamp(payload.optNullableString("updated_at")),
        )
    }

    suspend fun fetchBlockKeywordCatalog(): BlockKeywordCatalog = withContext(Dispatchers.IO) {
        val payload = execute("/automation/mobile/block-keywords", "GET").asObject()
        BlockKeywordCatalog(
            version = payload.optString("version", "0"),
            items = payload.optJSONArray("items").toBlockKeywords(),
            updatedAtMillis = parseTimestamp(payload.optNullableString("updated_at")),
        )
    }

    override suspend fun listTasks(): List<RemoteTask> = withContext(Dispatchers.IO) {
        execute("/automation/mobile/tasks", "GET").asArray().toRemoteTasks()
    }

    override suspend fun claimTask(taskId: Long): RemoteTask = withContext(Dispatchers.IO) {
        execute("/automation/mobile/tasks/$taskId/claim", "POST").asObject().toRemoteTask()
    }

    override suspend fun getTaskProgress(taskId: Long): RemoteTaskProgress = withContext(Dispatchers.IO) {
        execute("/automation/mobile/tasks/$taskId/progress", "GET").asObject().toProgress()
    }

    override suspend fun submitCheckpoint(
        taskId: Long,
        request: RemoteCheckpointRequest,
    ): RemoteCheckpointResponse = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("page_number", request.pageNumber)
            put("page_fingerprint", request.pageFingerprint)
            request.lastUserKey?.let { put("last_user_key", it) }
            request.lastUserName?.let { put("last_user_name", it) }
            put("visible_user_keys", JSONArray(request.visibleUserKeys))
        }
        execute("/automation/mobile/tasks/$taskId/checkpoint", "POST", body)
            .asObject()
            .toCheckpointResponse()
    }

    override suspend fun submitRecord(
        taskId: Long,
        request: RemoteRecordRequest,
    ): RemoteRecordSyncResponse = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("user_key", request.userKey)
            request.displayName?.let { put("display_name", it) }
            request.douyinId?.let { put("douyin_id", it) }
            put("status", request.status)
            request.lastAction?.let { put("last_action", it) }
            request.pageNumber?.let { put("page_number", it) }
            request.rowIndex?.let { put("row_index", it) }
            request.pageFingerprint?.let { put("page_fingerprint", it) }
            request.failureCode?.let { put("failure_code", it) }
            request.failureMessage?.let { put("failure_message", it) }
        }
        execute("/automation/mobile/tasks/$taskId/records", "POST", body)
            .asObject()
            .toRecordSyncResponse()
    }

    override suspend fun updateTaskStatus(
        taskId: Long,
        request: RemoteTaskStatusRequest,
    ): RemoteTask = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("status", request.status)
            request.errorCode?.let { put("error_code", it) }
            request.errorMessage?.let { put("error_message", it) }
        }
        execute("/automation/mobile/tasks/$taskId/status", "POST", body)
            .asObject()
            .toRemoteTask()
    }

    private fun execute(path: String, method: String, body: JSONObject? = null): Any {
        val connection = connectionFactory(URL(endpointUrl(path)))
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.doInput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${config.licenseToken}")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.let { BufferedInputStream(it).use { input -> input.readBytes().toString(StandardCharsets.UTF_8) } }
                .orEmpty()
            val envelope = runCatching { JSONObject(raw) }.getOrElse {
                throw AutomationGatewayException(statusCode, "授权服务返回了无法解析的响应")
            }
            if (statusCode !in 200..299 || !envelope.optBoolean("success", true)) {
                val message = envelope.optString("msg", "授权服务请求失败")
                throw AutomationGatewayException(statusCode, message)
            }
            return envelope.opt("data") ?: JSONObject.NULL
        } finally {
            connection.disconnect()
        }
    }

    private fun endpointUrl(path: String): String {
        val endpoint = config.endpoint.trim().trimEnd('/')
        val apiBase = when {
            endpoint.endsWith("/api/v1") -> endpoint
            endpoint.contains("/api/v1/") -> endpoint.substringBefore("/api/v1/") + "/api/v1"
            else -> "$endpoint/api/v1"
        }
        return apiBase + "/" + path.trimStart('/')
    }

    private fun Any?.asObject(): JSONObject = this as? JSONObject
        ?: throw AutomationGatewayException(200, "授权服务返回的数据格式错误")

    private fun Any?.asArray(): JSONArray = this as? JSONArray
        ?: throw AutomationGatewayException(200, "授权服务返回的列表格式错误")

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000
        const val DEFAULT_READ_TIMEOUT_MILLIS = 15_000
        const val PACKAGE_NAME = "com.example.douyinautomation"
        const val PROTOCOL_VERSION = 1
        val BACKEND_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun parseTimestamp(value: String?): Long? {
            if (value.isNullOrBlank()) return null
            return runCatching { Instant.parse(value).toEpochMilli() }.getOrElse {
                runCatching {
                    LocalDateTime.parse(value, BACKEND_DATE_TIME)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()
            }
        }

        fun JSONObject.optNullableString(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

        fun JSONArray?.toSearchPresets(): List<SearchPreset> = if (this == null) emptyList() else buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    SearchPreset(
                        id = item.optString("id"),
                        label = item.optString("label"),
                        keyword = item.optString("keyword"),
                        enabled = item.optBoolean("enabled", true),
                    ),
                )
            }
        }.filter { it.id.isNotBlank() && it.label.isNotBlank() && it.keyword.isNotBlank() }

        fun JSONArray?.toRegions(): List<RegionRule> = if (this == null) emptyList() else buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    RegionRule(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        prefix = item.optString("prefix"),
                        enabled = item.optBoolean("enabled", true),
                        sort = item.optInt("sort", 0),
                    ),
                )
            }
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.prefix.isNotBlank() }

        fun JSONArray?.toBlockKeywords(): List<BlockKeywordRule> = if (this == null) emptyList() else buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    BlockKeywordRule(
                        id = item.optString("id"),
                        keyword = item.optString("keyword"),
                        matchMode = item.optString("match_mode", "contains"),
                        enabled = item.optBoolean("enabled", true),
                        sort = item.optInt("sort", 0),
                    ),
                )
            }
        }.filter { it.id.isNotBlank() && it.keyword.isNotBlank() }

        fun JSONArray.toRemoteTasks(): List<RemoteTask> = buildList(length()) {
            for (index in 0 until length()) add(optJSONObject(index)?.toRemoteTask() ?: continue)
        }

        fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else buildList(length()) {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }

        fun JSONObject.toRemoteTask(): RemoteTask = RemoteTask(
            id = optLong("id"),
            code = optString("code"),
            name = optString("name"),
            keyword = optString("keyword"),
            regionName = optNullableString("region_name"),
            regionPrefix = optNullableString("region_prefix"),
            message = optString("message"),
            sendMode = optString("send_mode"),
            catalogVersion = optInt("catalog_version"),
            maxUsers = optInt("max_users"),
            status = optInt("status"),
            licenseId = if (isNull("license_id")) null else optLong("license_id"),
            totalCount = optInt("total_count"),
            processedCount = optInt("processed_count"),
            successCount = optInt("success_count"),
            failedCount = optInt("failed_count"),
            skippedCount = optInt("skipped_count"),
            lastUserKey = optNullableString("last_user_key"),
            lastUserName = optNullableString("last_user_name"),
            lastPageNumber = if (isNull("last_page_number")) null else optInt("last_page_number"),
            lastPageFingerprint = optNullableString("last_page_fingerprint"),
            checkpointVersion = optInt("checkpoint_version"),
            blockedKeywords = optJSONArray("blocked_keywords").toStringList(),
        )

        fun JSONObject.toProgress(): RemoteTaskProgress = RemoteTaskProgress(
            taskId = optLong("task_id"),
            status = optInt("status"),
            totalCount = optInt("total_count"),
            processedCount = optInt("processed_count"),
            pendingCount = optInt("pending_count"),
            successCount = optInt("success_count"),
            failedCount = optInt("failed_count"),
            skippedCount = optInt("skipped_count"),
            completionPercent = optDouble("completion_percent"),
            lastUserKey = optNullableString("last_user_key"),
            lastUserName = optNullableString("last_user_name"),
            lastPageNumber = if (isNull("last_page_number")) null else optInt("last_page_number"),
            lastPageFingerprint = optNullableString("last_page_fingerprint"),
            checkpointVersion = optInt("checkpoint_version"),
        )

        fun JSONObject.toCheckpointResponse(): RemoteCheckpointResponse = RemoteCheckpointResponse(
            taskId = optLong("task_id"),
            pageNumber = optInt("page_number"),
            pageFingerprint = optString("page_fingerprint"),
            checkpointVersion = optInt("checkpoint_version"),
            lastUserKey = optNullableString("last_user_key"),
            newUserKeys = optStringList("new_user_keys"),
            duplicateUserKeys = optStringList("duplicate_user_keys"),
            terminalUserKeys = optStringList("terminal_user_keys"),
            progress = optJSONObject("progress")?.toProgress()
                ?: throw AutomationGatewayException(200, "授权服务未返回任务进度"),
        )

        fun JSONObject.toRecordSyncResponse(): RemoteRecordSyncResponse = RemoteRecordSyncResponse(
            record = optJSONObject("record")?.toRecord()
                ?: throw AutomationGatewayException(200, "授权服务未返回用户记录"),
            deduplicated = optBoolean("deduplicated", false),
            progress = optJSONObject("progress")?.toProgress()
                ?: throw AutomationGatewayException(200, "授权服务未返回任务进度"),
        )

        fun JSONObject.toRecord(): RemoteTaskRecord = RemoteTaskRecord(
            taskId = optLong("task_id"),
            userKey = optString("user_key"),
            displayName = optNullableString("display_name"),
            douyinId = optNullableString("douyin_id"),
            status = optInt("status"),
            attemptCount = optInt("attempt_count"),
            lastAction = optNullableString("last_action"),
            lastPageNumber = if (isNull("last_page_number")) null else optInt("last_page_number"),
            lastRowIndex = if (isNull("last_row_index")) null else optInt("last_row_index"),
            lastPageFingerprint = optNullableString("last_page_fingerprint"),
            failureCode = optNullableString("failure_code"),
            failureMessage = optNullableString("failure_message"),
        )

        fun JSONObject.optStringList(key: String): List<String> =
            optJSONArray(key)?.let { values ->
                buildList(values.length()) {
                    for (index in 0 until values.length()) {
                        values.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }.orEmpty()
    }
}
