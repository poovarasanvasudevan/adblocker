package com.kiteapps.adblocker.data.db

import android.content.ContentValues
import android.database.Cursor
import com.kiteapps.adblocker.core.model.DnsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DnsLogRepository(
    private val dbHelper: AdBlockDatabaseHelper,
    private val scope: CoroutineScope
) {
    private val _logsFlow = MutableStateFlow<List<DnsLog>>(emptyList())
    val logsFlow: StateFlow<List<DnsLog>> = _logsFlow.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            refreshLogs()
        }
    }

    suspend fun refreshLogs() = withContext(Dispatchers.IO) {
        val list = getRecentLogs(limit = 100)
        _logsFlow.value = list
    }

    suspend fun insertLog(log: DnsLog): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(AdBlockDatabaseHelper.COL_LOG_DOMAIN, log.domain)
            put(AdBlockDatabaseHelper.COL_LOG_IS_BLOCKED, if (log.isBlocked) 1 else 0)
            put(AdBlockDatabaseHelper.COL_LOG_QUERY_TYPE, log.queryType)
            put(AdBlockDatabaseHelper.COL_LOG_LATENCY, log.latencyMs)
            put(AdBlockDatabaseHelper.COL_LOG_APP_NAME, log.appName)
            put(AdBlockDatabaseHelper.COL_LOG_PACKAGE_NAME, log.packageName)
            put(AdBlockDatabaseHelper.COL_LOG_RESOLVED_IP, log.resolvedIp)
            put(AdBlockDatabaseHelper.COL_LOG_BLOCKED_BY, log.blockedBy)
            put(AdBlockDatabaseHelper.COL_LOG_TIMESTAMP, log.timestamp)
        }
        val id = db.insert(AdBlockDatabaseHelper.TABLE_DNS_LOGS, null, values)
        val newEntry = log.copy(id = id)

        // Keep current stream up to date in memory
        val current = _logsFlow.value.toMutableList()
        current.add(0, newEntry)
        if (current.size > 150) {
            current.removeAt(current.size - 1)
        }
        _logsFlow.value = current

        id
    }

    suspend fun getRecentLogs(
        limit: Int = 100,
        filterBlockedOnly: Boolean? = null,
        searchQuery: String = ""
    ): List<DnsLog> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (filterBlockedOnly != null) {
            selectionParts.add("${AdBlockDatabaseHelper.COL_LOG_IS_BLOCKED} = ?")
            selectionArgs.add(if (filterBlockedOnly) "1" else "0")
        }

        if (searchQuery.isNotBlank()) {
            selectionParts.add("(${AdBlockDatabaseHelper.COL_LOG_DOMAIN} LIKE ? OR ${AdBlockDatabaseHelper.COL_LOG_APP_NAME} LIKE ?)")
            val arg = "%${searchQuery.trim()}%"
            selectionArgs.add(arg)
            selectionArgs.add(arg)
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val args = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null

        val cursor = db.query(
            AdBlockDatabaseHelper.TABLE_DNS_LOGS,
            null,
            selection,
            args,
            null,
            null,
            "${AdBlockDatabaseHelper.COL_LOG_TIMESTAMP} DESC",
            limit.toString()
        )

        val results = mutableListOf<DnsLog>()
        cursor.use { c ->
            while (c.moveToNext()) {
                results.add(c.toDnsLog())
            }
        }
        results
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(AdBlockDatabaseHelper.TABLE_DNS_LOGS, null, null)
        _logsFlow.value = emptyList()
    }

    private fun Cursor.toDnsLog(): DnsLog {
        return DnsLog(
            id = getLong(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_LOG_ID)),
            domain = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_LOG_DOMAIN)),
            isBlocked = getInt(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_LOG_IS_BLOCKED)) == 1,
            queryType = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_LOG_QUERY_TYPE)),
            latencyMs = getLong(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_LOG_LATENCY)),
            appName = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_LOG_APP_NAME)),
            packageName = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_LOG_PACKAGE_NAME)),
            resolvedIp = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_LOG_RESOLVED_IP)),
            blockedBy = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_LOG_BLOCKED_BY)),
            timestamp = getLong(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_LOG_TIMESTAMP))
        )
    }
}
