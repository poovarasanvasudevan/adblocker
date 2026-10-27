package com.kiteapps.adblocker.data.db

import android.content.ContentValues
import android.database.Cursor
import com.kiteapps.adblocker.core.model.CustomRule
import com.kiteapps.adblocker.core.model.RuleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class CustomRuleRepository(
    private val dbHelper: AdBlockDatabaseHelper,
    private val scope: CoroutineScope
) {
    private val allowedDomains = ConcurrentHashMap.newKeySet<String>()
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()

    private val _rulesFlow = MutableStateFlow<List<CustomRule>>(emptyList())
    val rulesFlow: StateFlow<List<CustomRule>> = _rulesFlow.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            refreshCache()
        }
    }

    suspend fun refreshCache() = withContext(Dispatchers.IO) {
        val rules = getAllRules()
        allowedDomains.clear()
        blockedDomains.clear()
        rules.forEach { rule ->
            val clean = rule.domain.lowercase().trim()
            if (rule.type == RuleType.ALLOW) {
                allowedDomains.add(clean)
            } else {
                blockedDomains.add(clean)
            }
        }
        _rulesFlow.value = rules
    }

    fun isWhitelisted(domain: String): Boolean {
        val d = domain.lowercase().trim()
        if (allowedDomains.contains(d)) return true
        var parent = d
        while (parent.contains('.')) {
            parent = parent.substringAfter('.')
            if (allowedDomains.contains(parent)) return true
        }
        return false
    }

    fun isBlacklisted(domain: String): Boolean {
        val d = domain.lowercase().trim()
        if (blockedDomains.contains(d)) return true
        var parent = d
        while (parent.contains('.')) {
            parent = parent.substringAfter('.')
            if (blockedDomains.contains(parent)) return true
        }
        return false
    }

    suspend fun addRule(domain: String, type: RuleType, note: String = "") = withContext(Dispatchers.IO) {
        val cleanDomain = domain.lowercase().trim()
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(AdBlockDatabaseHelper.COL_RULE_DOMAIN, cleanDomain)
            put(AdBlockDatabaseHelper.COL_RULE_TYPE, type.name)
            put(AdBlockDatabaseHelper.COL_RULE_NOTE, note)
            put(AdBlockDatabaseHelper.COL_RULE_TIMESTAMP, System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            AdBlockDatabaseHelper.TABLE_CUSTOM_RULES,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        refreshCache()
    }

    suspend fun removeRule(domain: String) = withContext(Dispatchers.IO) {
        val cleanDomain = domain.lowercase().trim()
        val db = dbHelper.writableDatabase
        db.delete(
            AdBlockDatabaseHelper.TABLE_CUSTOM_RULES,
            "${AdBlockDatabaseHelper.COL_RULE_DOMAIN} = ?",
            arrayOf(cleanDomain)
        )
        refreshCache()
    }

    suspend fun getAllRules(): List<CustomRule> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            AdBlockDatabaseHelper.TABLE_CUSTOM_RULES,
            null,
            null,
            null,
            null,
            null,
            "${AdBlockDatabaseHelper.COL_RULE_TIMESTAMP} DESC"
        )
        val list = mutableListOf<CustomRule>()
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(c.toCustomRule())
            }
        }
        list
    }

    private fun Cursor.toCustomRule(): CustomRule {
        val typeStr = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_RULE_TYPE))
        val type = try {
            RuleType.valueOf(typeStr)
        } catch (e: Exception) {
            RuleType.BLOCK
        }
        return CustomRule(
            id = getLong(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_RULE_ID)),
            domain = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_RULE_DOMAIN)),
            type = type,
            note = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_RULE_NOTE)),
            timestamp = getLong(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_RULE_TIMESTAMP))
        )
    }
}
