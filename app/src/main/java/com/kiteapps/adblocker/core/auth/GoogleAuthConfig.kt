package com.kiteapps.adblocker.core.auth

import com.google.android.gms.common.api.Scope

object GoogleAuthConfig {
    /**
     * Web Client ID from Firebase / Google Cloud Console OAuth 2.0 Credentials:
     * project: adblocker-b748a (client_type 3)
     */
    const val WEB_CLIENT_ID = "314074827050-112sthcvugajp5sud7hrn20penv3ai0f.apps.googleusercontent.com"

    /**
     * Google Drive AppData private folder scope
     * Allows reading and writing adblocker_cloud_backup.json inside the app's isolated hidden Drive folder
     */
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    val DRIVE_SCOPE = Scope(DRIVE_APPDATA_SCOPE)
}
