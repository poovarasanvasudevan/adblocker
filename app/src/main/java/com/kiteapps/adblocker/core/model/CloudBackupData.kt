package com.kiteapps.adblocker.core.model

import org.json.JSONArray
import org.json.JSONObject

data class CloudBackupData(
    val version: Int = 1,
    val deviceName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val customRules: List<CustomRuleBackupItem> = emptyList(),
    val customFilters: List<CustomFilterBackupItem> = emptyList(),
    val dnsSettings: DnsSettingsBackup = DnsSettingsBackup(),
    val appSettings: AppSettingsBackup = AppSettingsBackup()
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("version", version)
        root.put("deviceName", deviceName)
        root.put("timestamp", timestamp)

        // Custom Rules
        val rulesArray = JSONArray()
        for (r in customRules) {
            val obj = JSONObject()
            obj.put("domain", r.domain)
            obj.put("type", r.type)
            obj.put("note", r.note)
            rulesArray.put(obj)
        }
        root.put("customRules", rulesArray)

        // Custom Filters
        val filtersArray = JSONArray()
        for (f in customFilters) {
            val obj = JSONObject()
            obj.put("name", f.name)
            obj.put("url", f.url)
            obj.put("isEnabled", f.isEnabled)
            filtersArray.put(obj)
        }
        root.put("customFilters", filtersArray)

        // DNS Settings
        val dnsObj = JSONObject()
        dnsObj.put("providerId", dnsSettings.providerId)
        dnsObj.put("customDoHUrl", dnsSettings.customDoHUrl)
        root.put("dnsSettings", dnsObj)

        // App Settings
        val appObj = JSONObject()
        appObj.put("themeMode", appSettings.themeMode)
        appObj.put("useDynamicColor", appSettings.useDynamicColor)
        appObj.put("bootAutoStart", appSettings.bootAutoStart)
        appObj.put("showNotifications", appSettings.showNotifications)

        val bypassArray = JSONArray()
        appSettings.bypassedApps.forEach { bypassArray.put(it) }
        appObj.put("bypassedApps", bypassArray)

        val firewalledArray = JSONArray()
        appSettings.firewalledApps.forEach { firewalledArray.put(it) }
        appObj.put("firewalledApps", firewalledArray)

        root.put("appSettings", appObj)

        return root.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): CloudBackupData {
            val root = JSONObject(jsonStr)
            val version = root.optInt("version", 1)
            val deviceName = root.optString("deviceName", "Unknown Device")
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())

            val rules = mutableListOf<CustomRuleBackupItem>()
            val rulesArray = root.optJSONArray("customRules")
            if (rulesArray != null) {
                for (i in 0 until rulesArray.length()) {
                    val obj = rulesArray.getJSONObject(i)
                    rules.add(
                        CustomRuleBackupItem(
                            domain = obj.optString("domain", ""),
                            type = obj.optString("type", "BLOCK"),
                            note = obj.optString("note", "")
                        )
                    )
                }
            }

            val filters = mutableListOf<CustomFilterBackupItem>()
            val filtersArray = root.optJSONArray("customFilters")
            if (filtersArray != null) {
                for (i in 0 until filtersArray.length()) {
                    val obj = filtersArray.getJSONObject(i)
                    filters.add(
                        CustomFilterBackupItem(
                            name = obj.optString("name", "Custom Filter"),
                            url = obj.optString("url", ""),
                            isEnabled = obj.optBoolean("isEnabled", true)
                        )
                    )
                }
            }

            val dnsObj = root.optJSONObject("dnsSettings")
            val dnsSettings = DnsSettingsBackup(
                providerId = dnsObj?.optString("providerId", "cloudflare") ?: "cloudflare",
                customDoHUrl = dnsObj?.optString("customDoHUrl", "") ?: ""
            )

            val appObj = root.optJSONObject("appSettings")
            val bypassed = mutableListOf<String>()
            val bypassArray = appObj?.optJSONArray("bypassedApps")
            if (bypassArray != null) {
                for (i in 0 until bypassArray.length()) {
                    bypassed.add(bypassArray.getString(i))
                }
            }

            val firewalled = mutableListOf<String>()
            val firewalledArray = appObj?.optJSONArray("firewalledApps")
            if (firewalledArray != null) {
                for (i in 0 until firewalledArray.length()) {
                    firewalled.add(firewalledArray.getString(i))
                }
            }

            val appSettings = AppSettingsBackup(
                themeMode = appObj?.optString("themeMode", "system") ?: "system",
                useDynamicColor = appObj?.optBoolean("useDynamicColor", true) ?: true,
                bootAutoStart = appObj?.optBoolean("bootAutoStart", false) ?: false,
                showNotifications = appObj?.optBoolean("showNotifications", true) ?: true,
                bypassedApps = bypassed,
                firewalledApps = firewalled
            )

            return CloudBackupData(
                version = version,
                deviceName = deviceName,
                timestamp = timestamp,
                customRules = rules,
                customFilters = filters,
                dnsSettings = dnsSettings,
                appSettings = appSettings
            )
        }
    }
}

data class CustomRuleBackupItem(
    val domain: String,
    val type: String,
    val note: String
)

data class CustomFilterBackupItem(
    val name: String,
    val url: String,
    val isEnabled: Boolean
)

data class DnsSettingsBackup(
    val providerId: String = "cloudflare",
    val customDoHUrl: String = ""
)

data class AppSettingsBackup(
    val themeMode: String = "system",
    val useDynamicColor: Boolean = true,
    val bootAutoStart: Boolean = false,
    val showNotifications: Boolean = true,
    val bypassedApps: List<String> = emptyList(),
    val firewalledApps: List<String> = emptyList()
)

data class CloudBackupSummary(
    val rulesCount: Int,
    val filtersCount: Int,
    val timestamp: Long,
    val deviceName: String
)
