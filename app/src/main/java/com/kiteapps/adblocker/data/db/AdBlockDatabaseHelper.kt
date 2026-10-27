package com.kiteapps.adblocker.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AdBlockDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "adblocker.db"
        const val DATABASE_VERSION = 2

        const val TABLE_DNS_LOGS = "dns_logs"
        const val COL_LOG_ID = "id"
        const val COL_LOG_DOMAIN = "domain"
        const val COL_LOG_IS_BLOCKED = "is_blocked"
        const val COL_LOG_QUERY_TYPE = "query_type"
        const val COL_LOG_LATENCY = "latency_ms"
        const val COL_LOG_APP_NAME = "app_name"
        const val COL_LOG_PACKAGE_NAME = "package_name"
        const val COL_LOG_RESOLVED_IP = "resolved_ip"
        const val COL_LOG_BLOCKED_BY = "blocked_by"
        const val COL_LOG_TIMESTAMP = "timestamp"

        const val TABLE_FILTER_LISTS = "filter_lists"
        const val COL_FILTER_ID = "id"
        const val COL_FILTER_NAME = "name"
        const val COL_FILTER_DESC = "description"
        const val COL_FILTER_IS_ENABLED = "is_enabled"
        const val COL_FILTER_IS_BUILTIN = "is_builtin"
        const val COL_FILTER_CATEGORY = "category"
        const val COL_FILTER_RULE_COUNT = "rule_count"
        const val COL_FILTER_DOWNLOAD_URL = "download_url"
        const val COL_FILTER_TRIE_URL = "trie_url"
        const val COL_FILTER_BLOOM_URL = "bloom_url"
        const val COL_FILTER_LAST_UPDATED = "last_updated"
        const val COL_FILTER_IS_PRO = "is_pro"

        const val TABLE_CUSTOM_RULES = "custom_rules"
        const val COL_RULE_ID = "id"
        const val COL_RULE_DOMAIN = "domain"
        const val COL_RULE_TYPE = "rule_type"
        const val COL_RULE_NOTE = "note"
        const val COL_RULE_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_DNS_LOGS (
                $COL_LOG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_LOG_DOMAIN TEXT NOT NULL,
                $COL_LOG_IS_BLOCKED INTEGER NOT NULL,
                $COL_LOG_QUERY_TYPE TEXT DEFAULT 'A',
                $COL_LOG_LATENCY INTEGER DEFAULT 0,
                $COL_LOG_APP_NAME TEXT DEFAULT '',
                $COL_LOG_PACKAGE_NAME TEXT DEFAULT '',
                $COL_LOG_RESOLVED_IP TEXT DEFAULT '',
                $COL_LOG_BLOCKED_BY TEXT DEFAULT '',
                $COL_LOG_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_logs_timestamp ON $TABLE_DNS_LOGS ($COL_LOG_TIMESTAMP DESC)")
        db.execSQL("CREATE INDEX idx_logs_domain ON $TABLE_DNS_LOGS ($COL_LOG_DOMAIN)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_FILTER_LISTS (
                $COL_FILTER_ID TEXT PRIMARY KEY,
                $COL_FILTER_NAME TEXT NOT NULL,
                $COL_FILTER_DESC TEXT,
                $COL_FILTER_IS_ENABLED INTEGER NOT NULL DEFAULT 0,
                $COL_FILTER_IS_BUILTIN INTEGER NOT NULL DEFAULT 1,
                $COL_FILTER_CATEGORY TEXT DEFAULT 'ads',
                $COL_FILTER_RULE_COUNT INTEGER DEFAULT 0,
                $COL_FILTER_DOWNLOAD_URL TEXT NOT NULL,
                $COL_FILTER_TRIE_URL TEXT DEFAULT '',
                $COL_FILTER_BLOOM_URL TEXT DEFAULT '',
                $COL_FILTER_LAST_UPDATED INTEGER DEFAULT 0,
                $COL_FILTER_IS_PRO INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_CUSTOM_RULES (
                $COL_RULE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_RULE_DOMAIN TEXT NOT NULL UNIQUE,
                $COL_RULE_TYPE TEXT NOT NULL,
                $COL_RULE_NOTE TEXT DEFAULT '',
                $COL_RULE_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_FILTER_LISTS ADD COLUMN $COL_FILTER_IS_PRO INTEGER DEFAULT 0")
            } catch (e: Exception) {
                db.execSQL("DROP TABLE IF EXISTS $TABLE_DNS_LOGS")
                db.execSQL("DROP TABLE IF EXISTS $TABLE_FILTER_LISTS")
                db.execSQL("DROP TABLE IF EXISTS $TABLE_CUSTOM_RULES")
                onCreate(db)
            }
        } else {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_DNS_LOGS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_FILTER_LISTS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_CUSTOM_RULES")
            onCreate(db)
        }
    }
}
