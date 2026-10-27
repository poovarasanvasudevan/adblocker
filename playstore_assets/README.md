# Google Play Store Assets for AdBlocker

This directory contains the production-ready marketing assets, store graphics, and device screenshots generated in accordance with official **Google Play Store Listing Guidelines**.

---

## Directory Structure

```
playstore_assets/
├── store_assets/
│   ├── playstore_icon_512.png                 # App Icon (512 x 512, 32-bit PNG with alpha, < 1MB)
│   └── playstore_feature_graphic_1024x500.png # Feature Graphic Banner (1024 x 500, 24-bit PNG, no alpha, < 15MB)
│
├── screenshots_framed/                        # Framed Marketing Cards (1080 x 2400)
│   ├── 01_shield_home_framed.png              # System-Wide Protection
│   ├── 02_activity_logs_framed.png            # Live DNS Query Stream
│   ├── 03_filters_rules_framed.png            # 30+ Curated Filter Lists
│   ├── 04_apps_firewall_framed.png            # Per-App Network Firewall
│   ├── 05_settings_framed.png                 # Next-Gen Encrypted DNS (DoH/DoT)
│   ├── 06_analytics_framed.png                # Visual Traffic Analytics
│   ├── 07_browser_framed.png                  # Ad-Free Built-in Browser
│   └── 08_account_sync_framed.png             # Google Drive Cloud Sync
│
├── screenshots_raw/                           # Clean Raw Device Captures (1080 x 2400)
│   ├── 01_shield_home.png                     # Home Screen
│   ├── 02_activity_logs.png                   # Query Logs
│   ├── 03_filters_rules.png                   # Filters Screen
│   ├── 04_apps_firewall.png                   # Per-App Firewall
│   ├── 05_settings.png                        # Settings Screen
│   ├── 06_analytics.png                       # Analytics Dashboard
│   ├── 07_browser.png                         # In-App Browser
│   └── 08_account_sync.png                    # Account & Cloud Sync
│
└── generate_framed_screenshots.py             # Reproducible screenshot generator script
```

---

## Google Play Console Specifications Checklist

| Asset | Required Dimensions | Format | Max File Size | Status |
| :--- | :--- | :--- | :--- | :--- |
| **App Icon** | `512 x 512 px` | 32-bit PNG (with alpha) | 1,024 KB | Verified (460 KB) |
| **Feature Graphic** | `1024 x 500 px` | 24-bit PNG / JPEG (no alpha) | 15 MB | Verified (784 KB) |
| **Phone Screenshots** | Min 2, Max 8 (`1080 x 2400` / 9:20) | 24-bit PNG or JPEG | 8 MB each | Verified (8 cards, 172KB-505KB) |

---

## Instructions for Uploading to Google Play Console

1. Navigate to [Google Play Console](https://play.google.com/console).
2. Select your app (`AdBlocker`).
3. Under the **Grow** section on the left sidebar, click **Store presence** > **Main store listing**.
4. **App Icon**: Upload `playstore_assets/store_assets/playstore_icon_512.png`.
5. **Feature Graphic**: Upload `playstore_assets/store_assets/playstore_feature_graphic_1024x500.png`.
6. **Phone Screenshots**: Drag and drop all 8 files from `playstore_assets/screenshots_framed/` in order (01 through 08).
7. Save changes and submit for review.
