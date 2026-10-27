package com.kiteapps.adblocker.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.kiteapps.adblocker.core.config.FilterApiConfig
import com.kiteapps.adblocker.core.model.FilterList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class FilterRepository(
    private val context: Context,
    private val dbHelper: AdBlockDatabaseHelper,
    private val scope: CoroutineScope
) {
    var isProUser: Boolean = false
        private set

    private val syncMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val filtersDir = File(context.filesDir, "filters").apply { mkdirs() }
    private val starterBlockedDomains = ConcurrentHashMap.newKeySet<String>()

    private val _filtersFlow = MutableStateFlow<List<FilterList>>(emptyList())
    val filtersFlow: StateFlow<List<FilterList>> = _filtersFlow.asStateFlow()

    private val _isUpdatingFlow = MutableStateFlow(false)
    val isUpdatingFlow: StateFlow<Boolean> = _isUpdatingFlow.asStateFlow()

    private val _isCatalogLoadedFlow = MutableStateFlow(false)
    val isCatalogLoadedFlow: StateFlow<Boolean> = _isCatalogLoadedFlow.asStateFlow()

    @Volatile
    private var adTriePaths: String = ""
    @Volatile
    private var adBloomPaths: String = ""

    init {
        scope.launch(Dispatchers.IO) {
            loadStarterBlocklist()
            refreshFilters()
            syncCatalogFromRemote(isProUser)
            refreshFilters()
            compileOrLoadActiveTries()
        }
    }

    private fun loadStarterBlocklist() {
        try {
            context.assets.open("preset/blocklist_starter.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim().lowercase()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        starterBlockedDomains.add(trimmed)
                    }
                }
            }
            Timber.d("Loaded ${starterBlockedDomains.size} starter blocked domains")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load starter blocklist")
        }
    }

    suspend fun refreshFilters() = withContext(Dispatchers.IO) {
        val list = getAllFiltersFromDb()
        _filtersFlow.value = list
    }

    fun isDomainBlocked(domain: String): Boolean {
        val clean = domain.lowercase().trim()
        if (starterBlockedDomains.contains(clean)) return true
        var parent = clean
        while (parent.contains('.')) {
            parent = parent.substringAfter('.')
            if (starterBlockedDomains.contains(parent)) return true
        }

        // Check active trie files if present
        val paths = adTriePaths.split(",").filter { it.isNotBlank() }
        for (path in paths) {
            val file = File(path)
            if (file.exists() && file.length() > 0) {
                try {
                    if (tunnel.Tunnel.checkDomainInTrieFile(file.absolutePath, clean)) {
                        return true
                    }
                } catch (e: Exception) {
                    // Ignore trie query errors
                }
            }
        }

        return false
    }

    fun getAdTriePaths(): String = adTriePaths
    fun getAdBloomPaths(): String = adBloomPaths

    suspend fun toggleFilter(filterId: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val filter = getFilterById(filterId)
        if (filter != null && filter.isPro && !isProUser && isEnabled) {
            Timber.w("Cannot enable Pro filter $filterId without Pro subscription")
            return@withContext
        }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(AdBlockDatabaseHelper.COL_FILTER_IS_ENABLED, if (isEnabled) 1 else 0)
        }
        db.update(
            AdBlockDatabaseHelper.TABLE_FILTER_LISTS,
            values,
            "${AdBlockDatabaseHelper.COL_FILTER_ID} = ?",
            arrayOf(filterId)
        )
        refreshFilters()
        compileOrLoadActiveTries()
    }

    suspend fun addCustomFilter(name: String, url: String) = withContext(Dispatchers.IO) {
        val id = "custom_" + System.currentTimeMillis()
        val filter = FilterList(
            id = id,
            name = name,
            description = "Custom User Filter: $url",
            isEnabled = true,
            isBuiltIn = false,
            category = "custom",
            ruleCount = 0,
            downloadUrl = url,
            lastUpdated = System.currentTimeMillis()
        )
        saveFilterToDb(filter)
        refreshFilters()
        downloadAndCompileFilter(filter)
    }

    suspend fun updateAllActiveFilters() = withContext(Dispatchers.IO) {
        _isUpdatingFlow.value = true
        try {
            syncCatalogFromRemote(isProUser)
            val enabled = _filtersFlow.value.filter { it.isEnabled }
            for (filter in enabled) {
                downloadAndCompileFilter(filter)
            }
            compileOrLoadActiveTries()
        } finally {
            _isUpdatingFlow.value = false
        }
    }

    suspend fun setProStatusAndSync(isPro: Boolean) {
        if (isProUser != isPro || getFilterCountInDb() == 0) {
            isProUser = isPro
            syncCatalogFromRemote(isPro)
        }
    }

    suspend fun syncCatalogFromRemote(isPro: Boolean = isProUser): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            isProUser = isPro
            val targetUrl = FilterApiConfig.getCatalogUrl()
            Timber.i("Syncing complete filter catalog strictly from API: $targetUrl (isPro=$isPro)")
            var loaded = false

            // Fetch exclusively from live API URL with proper headers
            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "AdBlocker-Android/1.0")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyString = resp.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            parseAndSaveFilterJson(bodyString, isPro)
                            loaded = true
                            _isCatalogLoadedFlow.value = true
                            Timber.d("Filter catalog successfully synced from API ($targetUrl)")
                        }
                    } else {
                        Timber.w("API request returned status code ${resp.code} for $targetUrl")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not reach filter API ($targetUrl). No offline fallback asset is bundled.")
            }

            if (loaded) {
                compileOrLoadActiveTries()
            }
            refreshFilters()
            loaded
        }
    }

    private fun parseAndSaveFilterJson(jsonString: String, isPro: Boolean) {
        val array = JSONArray(jsonString)

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.optString("id", "")
            if (id.isBlank()) continue

            // Determine if filter item is Pro
            val isProItem = if (obj.has("pro")) {
                obj.optBoolean("pro", false)
            } else {
                !FilterApiConfig.DEFAULT_FREE_FILTER_IDS.contains(id)
            }

            val existing = getFilterById(id)
            val isEnabled = if (existing != null) {
                // If user is Free, Pro items must remain disabled
                if (isProItem && !isPro) false else existing.isEnabled
            } else {
                // Default enabled only for core non-pro filters on first install
                !isProItem && (id == "adguard_base" || id == "adguard_mobile" || id == "stevenblack")
            }

            val filter = FilterList(
                id = id,
                name = obj.optString("name", "Filter $i"),
                description = obj.optString("description", ""),
                isEnabled = isEnabled,
                isBuiltIn = true,
                category = obj.optString("category", "ads"),
                ruleCount = obj.optInt("ruleCount", 0),
                downloadUrl = obj.optString("downloadUrl", ""),
                trieUrl = obj.optString("trieUrl", ""),
                bloomUrl = obj.optString("bloomUrl", ""),
                lastUpdated = System.currentTimeMillis(),
                isPro = isProItem
            )
            saveFilterToDb(filter)
        }

        // If user is not Pro, ensure any Pro filters are strictly disabled
        if (!isPro) {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(AdBlockDatabaseHelper.COL_FILTER_IS_ENABLED, 0)
            }
            db.update(
                AdBlockDatabaseHelper.TABLE_FILTER_LISTS,
                values,
                "${AdBlockDatabaseHelper.COL_FILTER_IS_PRO} = 1",
                null
            )
        }
    }

    fun getFilterById(id: String): FilterList? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            AdBlockDatabaseHelper.TABLE_FILTER_LISTS,
            null,
            "${AdBlockDatabaseHelper.COL_FILTER_ID} = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToNext()) it.toFilterList() else null
        }
    }

    private suspend fun downloadAndCompileFilter(filter: FilterList) = withContext(Dispatchers.IO) {
        try {
            val trieFile = File(filtersDir, "${filter.id}.trie")
            val bloomFile = File(filtersDir, "${filter.id}.bloom")

            // If pre-compiled trie/bloom URLs are provided, fetch directly
            if (filter.trieUrl.isNotBlank() && filter.bloomUrl.isNotBlank()) {
                val trieReq = Request.Builder().url(filter.trieUrl).build()
                client.newCall(trieReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            trieFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                val bloomReq = Request.Builder().url(filter.bloomUrl).build()
                client.newCall(bloomReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            bloomFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            } else if (filter.downloadUrl.isNotBlank()) {
                // Download raw text and compile using Go tunnel engine
                val tempTextFile = File(filtersDir, "${filter.id}_temp.txt")
                val req = Request.Builder().url(filter.downloadUrl).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            tempTextFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }

                if (tempTextFile.exists() && tempTextFile.length() > 0) {
                    val count = tunnel.Tunnel.compileFilterList(
                        tempTextFile.absolutePath,
                        trieFile.absolutePath,
                        bloomFile.absolutePath
                    )
                    tempTextFile.delete()
                    Timber.d("Compiled filter ${filter.name} with $count rules")

                    // Update rule count in database
                    val db = dbHelper.writableDatabase
                    val values = ContentValues().apply {
                        put(AdBlockDatabaseHelper.COL_FILTER_RULE_COUNT, count)
                        put(AdBlockDatabaseHelper.COL_FILTER_LAST_UPDATED, System.currentTimeMillis())
                    }
                    db.update(
                        AdBlockDatabaseHelper.TABLE_FILTER_LISTS,
                        values,
                        "${AdBlockDatabaseHelper.COL_FILTER_ID} = ?",
                        arrayOf(filter.id)
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download/compile filter ${filter.name}")
        }
    }

    private suspend fun compileOrLoadActiveTries() = withContext(Dispatchers.IO) {
        val enabled = _filtersFlow.value.filter { it.isEnabled }
        val trieList = mutableListOf<String>()
        val bloomList = mutableListOf<String>()

        for (f in enabled) {
            val trie = File(filtersDir, "${f.id}.trie")
            val bloom = File(filtersDir, "${f.id}.bloom")
            if (trie.exists() && trie.length() > 0) {
                trieList.add(trie.absolutePath)
            }
            if (bloom.exists() && bloom.length() > 0) {
                bloomList.add(bloom.absolutePath)
            }
        }

        adTriePaths = trieList.joinToString(",")
        adBloomPaths = bloomList.joinToString(",")
        Timber.d("Active tries configured: ${trieList.size} files")
    }

    private fun getFilterCountInDb(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${AdBlockDatabaseHelper.TABLE_FILTER_LISTS}", null)
        return cursor.use { if (it.moveToNext()) it.getInt(0) else 0 }
    }

    private fun saveFilterToDb(filter: FilterList) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(AdBlockDatabaseHelper.COL_FILTER_ID, filter.id)
            put(AdBlockDatabaseHelper.COL_FILTER_NAME, filter.name)
            put(AdBlockDatabaseHelper.COL_FILTER_DESC, filter.description)
            put(AdBlockDatabaseHelper.COL_FILTER_IS_ENABLED, if (filter.isEnabled) 1 else 0)
            put(AdBlockDatabaseHelper.COL_FILTER_IS_BUILTIN, if (filter.isBuiltIn) 1 else 0)
            put(AdBlockDatabaseHelper.COL_FILTER_CATEGORY, filter.category)
            put(AdBlockDatabaseHelper.COL_FILTER_RULE_COUNT, filter.ruleCount)
            put(AdBlockDatabaseHelper.COL_FILTER_DOWNLOAD_URL, filter.downloadUrl)
            put(AdBlockDatabaseHelper.COL_FILTER_TRIE_URL, filter.trieUrl)
            put(AdBlockDatabaseHelper.COL_FILTER_BLOOM_URL, filter.bloomUrl)
            put(AdBlockDatabaseHelper.COL_FILTER_LAST_UPDATED, filter.lastUpdated)
            put(AdBlockDatabaseHelper.COL_FILTER_IS_PRO, if (filter.isPro) 1 else 0)
        }
        db.insertWithOnConflict(
            AdBlockDatabaseHelper.TABLE_FILTER_LISTS,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAllFilters(): List<FilterList> = getAllFiltersFromDb()

    private fun getAllFiltersFromDb(): List<FilterList> {
        val db = dbHelper.readableDatabase
        val orderBy = if (!isProUser) {
            "${AdBlockDatabaseHelper.COL_FILTER_IS_PRO} ASC, ${AdBlockDatabaseHelper.COL_FILTER_IS_ENABLED} DESC, ${AdBlockDatabaseHelper.COL_FILTER_NAME} ASC"
        } else {
            "${AdBlockDatabaseHelper.COL_FILTER_IS_ENABLED} DESC, ${AdBlockDatabaseHelper.COL_FILTER_NAME} ASC"
        }
        val cursor = db.query(
            AdBlockDatabaseHelper.TABLE_FILTER_LISTS,
            null,
            null,
            null,
            null,
            null,
            orderBy
        )
        val list = mutableListOf<FilterList>()
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(c.toFilterList())
            }
        }
        return list
    }

    private fun Cursor.toFilterList(): FilterList {
        val proColIdx = getColumnIndex(AdBlockDatabaseHelper.COL_FILTER_IS_PRO)
        val isPro = if (proColIdx != -1) getInt(proColIdx) == 1 else false

        return FilterList(
            id = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_ID)),
            name = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_NAME)),
            description = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_DESC)),
            isEnabled = getInt(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_IS_ENABLED)) == 1,
            isBuiltIn = getInt(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_IS_BUILTIN)) == 1,
            category = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_CATEGORY)),
            ruleCount = getInt(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_RULE_COUNT)),
            downloadUrl = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_DOWNLOAD_URL)),
            trieUrl = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_TRIE_URL)),
            bloomUrl = getString(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_BLOOM_URL)),
            lastUpdated = getLong(getColumnIndexOrThrow(AdBlockDatabaseHelper.COL_FILTER_LAST_UPDATED)),
            isPro = isPro
        )
    }
}
