@file:Suppress("DEPRECATION")

package com.kiteapps.adblocker.data.sync

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kiteapps.adblocker.core.auth.GoogleAuthConfig
import com.kiteapps.adblocker.core.model.*
import com.kiteapps.adblocker.data.db.CustomRuleRepository
import com.kiteapps.adblocker.data.db.FilterRepository
import com.kiteapps.adblocker.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class GoogleDriveSyncManager(
    private val context: Context,
    private val customRuleRepo: CustomRuleRepository,
    private val filterRepo: FilterRepository,
    private val prefs: PreferencesManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val localBackupFile = File(context.filesDir, "adblocker_cloud_backup.json")

    /**
     * Build the unified backup data structure from all user data sources
     */
    suspend fun createBackupData(): CloudBackupData = withContext(Dispatchers.IO) {
        val rules = customRuleRepo.getAllRules().map {
            CustomRuleBackupItem(
                domain = it.domain,
                type = it.type.name,
                note = it.note
            )
        }

        val filters = filterRepo.getAllFilters().filter { !it.isBuiltIn }.map {
            CustomFilterBackupItem(
                name = it.name,
                url = it.downloadUrl,
                isEnabled = it.isEnabled
            )
        }

        val dnsSettings = DnsSettingsBackup(
            providerId = prefs.dnsProviderId.first(),
            customDoHUrl = prefs.customDnsUrl.first()
        )

        val appSettings = AppSettingsBackup(
            themeMode = prefs.themeMode.first(),
            useDynamicColor = prefs.useDynamicColor.first(),
            bootAutoStart = prefs.bootAutoStart.first(),
            showNotifications = prefs.showNotifications.first(),
            bypassedApps = prefs.bypassedApps.first().toList(),
            firewalledApps = prefs.firewalledApps.first().toList()
        )

        CloudBackupData(
            version = 1,
            deviceName = UserProfile.defaultDeviceName(),
            timestamp = System.currentTimeMillis(),
            customRules = rules,
            customFilters = filters,
            dnsSettings = dnsSettings,
            appSettings = appSettings
        )
    }

    /**
     * Restore all user data from backup data payload
     */
    suspend fun restoreBackupData(data: CloudBackupData): CloudBackupSummary = withContext(Dispatchers.IO) {
        // 1. Restore Custom Domain Rules
        for (item in data.customRules) {
            val ruleType = try {
                RuleType.valueOf(item.type)
            } catch (e: Exception) {
                RuleType.BLOCK
            }
            customRuleRepo.addRule(item.domain, ruleType, item.note)
        }

        // 2. Restore Custom Filters
        for (filter in data.customFilters) {
            filterRepo.addCustomFilter(filter.name, filter.url)
        }

        // 3. Restore DNS Provider & Custom DoH URL
        if (data.dnsSettings.providerId.isNotBlank()) {
            prefs.setDnsProviderId(data.dnsSettings.providerId)
        }
        if (data.dnsSettings.customDoHUrl.isNotBlank()) {
            prefs.setCustomDnsUrl(data.dnsSettings.customDoHUrl)
        }

        // 4. Restore App Settings
        prefs.setThemeMode(data.appSettings.themeMode)
        prefs.setDynamicColor(data.appSettings.useDynamicColor)
        prefs.setBootAutoStart(data.appSettings.bootAutoStart)
        prefs.setShowNotifications(data.appSettings.showNotifications)

        for (pkg in data.appSettings.bypassedApps) {
            prefs.setAppBypassed(pkg, true)
        }
        for (pkg in data.appSettings.firewalledApps) {
            prefs.setAppFirewalled(pkg, true)
        }

        CloudBackupSummary(
            rulesCount = data.customRules.size,
            filtersCount = data.customFilters.size,
            timestamp = data.timestamp,
            deviceName = data.deviceName
        )
    }

    /**
     * Upload backup to Google Drive AppData folder
     */
    suspend fun uploadToGoogleDrive(account: GoogleSignInAccount?): Result<CloudBackupSummary> = withContext(Dispatchers.IO) {
        try {
            val backupData = createBackupData()
            val jsonString = backupData.toJson()

            // Always write local mirror cache
            localBackupFile.writeText(jsonString)

            val googleAccount: Account? = account?.account
            if (googleAccount != null) {
                val token = getOAuthToken(googleAccount)
                if (token.isNotBlank()) {
                    val existingFileId = findExistingDriveBackupFileId(token)

                    if (existingFileId != null) {
                        // Update existing file content
                        val req = Request.Builder()
                            .url("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media")
                            .header("Authorization", "Bearer $token")
                            .patch(jsonString.toRequestBody("application/json".toMediaType()))
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                Timber.w("Drive PATCH returned HTTP ${resp.code}: ${resp.body?.string()}")
                            }
                        }
                    } else {
                        // Create multipart upload in appDataFolder
                        val metadataJson = JSONObject().apply {
                            put("name", "adblocker_cloud_backup.json")
                            val parents = org.json.JSONArray().apply { put("appDataFolder") }
                            put("parents", parents)
                        }

                        val multipart = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                "metadata",
                                null,
                                metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                            )
                            .addFormDataPart(
                                "file",
                                "adblocker_cloud_backup.json",
                                jsonString.toRequestBody("application/json".toMediaType())
                            )
                            .build()

                        val req = Request.Builder()
                            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                            .header("Authorization", "Bearer $token")
                            .post(multipart)
                            .build()

                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                Timber.w("Drive POST returned HTTP ${resp.code}: ${resp.body?.string()}")
                            }
                        }
                    }
                }
            }

            prefs.setLastCloudBackupTime(backupData.timestamp)

            Result.success(
                CloudBackupSummary(
                    rulesCount = backupData.customRules.size,
                    filtersCount = backupData.customFilters.size,
                    timestamp = backupData.timestamp,
                    deviceName = backupData.deviceName
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload backup to Google Drive")
            Result.failure(e)
        }
    }

    /**
     * Download latest backup from Google Drive or local cache
     */
    suspend fun downloadFromGoogleDrive(account: GoogleSignInAccount?): Result<CloudBackupData> = withContext(Dispatchers.IO) {
        try {
            val googleAccount: Account? = account?.account
            var driveJson: String? = null

            if (googleAccount != null) {
                try {
                    val token = getOAuthToken(googleAccount)
                    if (token.isNotBlank()) {
                        val fileId = findExistingDriveBackupFileId(token)
                        if (fileId != null) {
                            val req = Request.Builder()
                                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                                .header("Authorization", "Bearer $token")
                                .get()
                                .build()

                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    driveJson = resp.body?.string()
                                    // Update local cached mirror
                                    driveJson?.let { localBackupFile.writeText(it) }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Drive fetch failed, falling back to local cache")
                }
            }

            val finalJson = driveJson ?: if (localBackupFile.exists()) localBackupFile.readText() else null

            if (finalJson == null || finalJson.isBlank()) {
                return@withContext Result.failure(Exception("No backup file found in Google Drive appDataFolder"))
            }

            val parsed = CloudBackupData.fromJson(finalJson)
            Result.success(parsed)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download backup from Google Drive")
            Result.failure(e)
        }
    }

    private fun getOAuthToken(account: Account): String {
        return try {
            val scopeString = "oauth2:${GoogleAuthConfig.DRIVE_APPDATA_SCOPE}"
            GoogleAuthUtil.getToken(context, account, scopeString)
        } catch (e: UserRecoverableAuthException) {
            Timber.w(e, "User recoverable auth exception for Drive scope")
            ""
        } catch (e: Exception) {
            Timber.e(e, "Error obtaining Google OAuth token for Drive")
            ""
        }
    }

    private fun findExistingDriveBackupFileId(token: String): String? {
        val queryUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='adblocker_cloud_backup.json'&fields=files(id,name,modifiedTime,size)"
        val req = Request.Builder()
            .url(queryUrl)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).optString("id")
                }
            }
        }
        return null
    }
}
