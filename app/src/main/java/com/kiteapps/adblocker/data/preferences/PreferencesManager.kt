package com.kiteapps.adblocker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.kiteapps.adblocker.core.model.UserProfile
import com.kiteapps.adblocker.core.model.UserTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "adblocker_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_VPN_ENABLED = booleanPreferencesKey("vpn_enabled")
        val KEY_DNS_PROVIDER_ID = stringPreferencesKey("dns_provider_id")
        val KEY_CUSTOM_DNS_URL = stringPreferencesKey("custom_doh_url")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_BOOT_AUTO_START = booleanPreferencesKey("boot_auto_start")
        val KEY_SHOW_NOTIFICATIONS = booleanPreferencesKey("show_notifications")
        val KEY_BYPASSED_APPS = stringSetPreferencesKey("bypassed_apps")
        val KEY_FIREWALLED_APPS = stringSetPreferencesKey("firewalled_apps")
        val KEY_TOTAL_BLOCKED = longPreferencesKey("total_blocked")
        val KEY_TOTAL_QUERIES = longPreferencesKey("total_queries")

        // User Auth & Profile
        val KEY_AUTH_COMPLETED_OR_SKIPPED = booleanPreferencesKey("auth_completed_or_skipped")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_USER_AVATAR_URL = stringPreferencesKey("user_avatar_url")
        val KEY_AUTH_PROVIDER = stringPreferencesKey("auth_provider")
        val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        val KEY_LAST_CLOUD_BACKUP_TIME = longPreferencesKey("last_cloud_backup_time")
        val KEY_USER_TIER = stringPreferencesKey("user_tier")
    }

    private val dataStore = context.dataStore

    val isVpnEnabled: Flow<Boolean> = dataStore.data.safeMap { it[KEY_VPN_ENABLED] ?: false }
    val dnsProviderId: Flow<String> = dataStore.data.safeMap { it[KEY_DNS_PROVIDER_ID] ?: "cloudflare" }
    val customDnsUrl: Flow<String> = dataStore.data.safeMap { it[KEY_CUSTOM_DNS_URL] ?: "" }
    val themeMode: Flow<String> = dataStore.data.safeMap { it[KEY_THEME_MODE] ?: "system" }
    val useDynamicColor: Flow<Boolean> = dataStore.data.safeMap { it[KEY_DYNAMIC_COLOR] ?: true }
    val bootAutoStart: Flow<Boolean> = dataStore.data.safeMap { it[KEY_BOOT_AUTO_START] ?: false }
    val showNotifications: Flow<Boolean> = dataStore.data.safeMap { it[KEY_SHOW_NOTIFICATIONS] ?: true }
    val bypassedApps: Flow<Set<String>> = dataStore.data.safeMap { it[KEY_BYPASSED_APPS] ?: emptySet() }
    val firewalledApps: Flow<Set<String>> = dataStore.data.safeMap { it[KEY_FIREWALLED_APPS] ?: emptySet() }
    val totalBlocked: Flow<Long> = dataStore.data.safeMap { it[KEY_TOTAL_BLOCKED] ?: 0L }
    val totalQueries: Flow<Long> = dataStore.data.safeMap { it[KEY_TOTAL_QUERIES] ?: 0L }
    val lastCloudBackupTime: Flow<Long> = dataStore.data.safeMap { it[KEY_LAST_CLOUD_BACKUP_TIME] ?: 0L }
    val userTier: Flow<UserTier> = dataStore.data.safeMap { prefs ->
        UserTier.fromId(prefs[KEY_USER_TIER])
    }

    val authCompletedOrSkipped: Flow<Boolean> = dataStore.data.safeMap { it[KEY_AUTH_COMPLETED_OR_SKIPPED] ?: false }

    val userProfile: Flow<UserProfile> = dataStore.data.safeMap { prefs ->
        val isLoggedIn = prefs[KEY_IS_LOGGED_IN] ?: false
        val defaultDevice = UserProfile.defaultDeviceName()
        val deviceName = prefs[KEY_DEVICE_NAME] ?: defaultDevice
        val authProvider = prefs[KEY_AUTH_PROVIDER] ?: if (isLoggedIn) "google" else "guest"
        val name = prefs[KEY_USER_NAME] ?: "$deviceName Profile"
        val email = prefs[KEY_USER_EMAIL] ?: if (isLoggedIn) "" else "guest@device.local"
        val avatar = prefs[KEY_USER_AVATAR_URL] ?: ""
        val id = prefs[KEY_USER_ID] ?: ""

        UserProfile(
            isLoggedIn = isLoggedIn,
            userId = id,
            displayName = name,
            email = email,
            avatarUrl = avatar,
            authProvider = authProvider,
            deviceName = deviceName
        )
    }

    suspend fun setAuthSkipped(deviceName: String = UserProfile.defaultDeviceName()) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTH_COMPLETED_OR_SKIPPED] = true
            prefs[KEY_IS_LOGGED_IN] = false
            prefs[KEY_AUTH_PROVIDER] = "guest"
            prefs[KEY_DEVICE_NAME] = deviceName
            prefs[KEY_USER_NAME] = "$deviceName Profile"
            prefs[KEY_USER_EMAIL] = "guest@device.local"
            prefs[KEY_USER_AVATAR_URL] = ""
            prefs[KEY_USER_ID] = "guest_${System.currentTimeMillis()}"
        }
    }

    suspend fun setGoogleUser(
        id: String,
        name: String,
        email: String,
        avatarUrl: String,
        deviceName: String = UserProfile.defaultDeviceName()
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTH_COMPLETED_OR_SKIPPED] = true
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_AUTH_PROVIDER] = "google"
            prefs[KEY_USER_ID] = id
            prefs[KEY_USER_NAME] = name.ifBlank { "Google User" }
            prefs[KEY_USER_EMAIL] = email
            prefs[KEY_USER_AVATAR_URL] = avatarUrl
            prefs[KEY_DEVICE_NAME] = deviceName
        }
    }

    suspend fun signOut(deviceName: String = UserProfile.defaultDeviceName()) {
        dataStore.edit { prefs ->
            // Keep authCompletedOrSkipped as true, but reset to guest device profile
            prefs[KEY_IS_LOGGED_IN] = false
            prefs[KEY_AUTH_PROVIDER] = "guest"
            prefs[KEY_USER_ID] = "guest_${System.currentTimeMillis()}"
            prefs[KEY_USER_NAME] = "$deviceName Profile"
            prefs[KEY_USER_EMAIL] = "guest@device.local"
            prefs[KEY_USER_AVATAR_URL] = ""
            prefs[KEY_DEVICE_NAME] = deviceName
        }
    }

    suspend fun setVpnEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_VPN_ENABLED] = enabled }
    }

    suspend fun setDnsProviderId(id: String) {
        dataStore.edit { it[KEY_DNS_PROVIDER_ID] = id }
    }

    suspend fun setCustomDnsUrl(url: String) {
        dataStore.edit { it[KEY_CUSTOM_DNS_URL] = url }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setBootAutoStart(enabled: Boolean) {
        dataStore.edit { it[KEY_BOOT_AUTO_START] = enabled }
    }

    suspend fun setShowNotifications(enabled: Boolean) {
        dataStore.edit { it[KEY_SHOW_NOTIFICATIONS] = enabled }
    }

    suspend fun setAppBypassed(packageName: String, bypassed: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BYPASSED_APPS]?.toMutableSet() ?: mutableSetOf()
            if (bypassed) current.add(packageName) else current.remove(packageName)
            prefs[KEY_BYPASSED_APPS] = current
        }
    }

    suspend fun setAppFirewalled(packageName: String, firewalled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_FIREWALLED_APPS]?.toMutableSet() ?: mutableSetOf()
            if (firewalled) current.add(packageName) else current.remove(packageName)
            prefs[KEY_FIREWALLED_APPS] = current
        }
    }

    suspend fun incrementQueryStats(blocked: Boolean) {
        dataStore.edit { prefs ->
            val currentQueries = prefs[KEY_TOTAL_QUERIES] ?: 0L
            prefs[KEY_TOTAL_QUERIES] = currentQueries + 1L
            if (blocked) {
                val currentBlocked = prefs[KEY_TOTAL_BLOCKED] ?: 0L
                prefs[KEY_TOTAL_BLOCKED] = currentBlocked + 1L
            }
        }
    }

    suspend fun resetStats() {
        dataStore.edit { prefs ->
            prefs[KEY_TOTAL_QUERIES] = 0L
            prefs[KEY_TOTAL_BLOCKED] = 0L
        }
    }

    suspend fun setLastCloudBackupTime(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CLOUD_BACKUP_TIME] = timestamp
        }
    }

    suspend fun setUserTier(tier: UserTier) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_TIER] = tier.id
        }
    }

    private fun <T> Flow<Preferences>.safeMap(transform: (Preferences) -> T): Flow<T> {
        return catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }.map(transform)
    }
}
