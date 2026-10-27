# 🚀 Google Play Console: In-App Billing & Subscriptions Setup Guide

This guide provides step-by-step instructions to configure **Google Play In-App Billing** for **AdBlocker Pro** in the [Google Play Console](https://play.google.com/console).

The app code has already been built and tested with Google Play Billing v7 (`com.android.billingclient:billing-ktx:7.1.1`). Once you configure the matching Product IDs in Play Console, real in-app purchases and subscriptions will activate automatically.

---

## 📋 Exact Product IDs Configured in the App

| Product Type | Exact Product ID (SKU) | Suggested Price | Recommended Offer / Base Plan |
| :--- | :--- | :--- | :--- |
| **Subscription** | `adblocker_pro_monthly` | `$1.99 / month` | Auto-renewing, 1 month |
| **Subscription** | `adblocker_pro_annual` | `$14.99 / year` | Auto-renewing, 1 year + **7-Day Free Trial** |
| **In-App Purchase** | `adblocker_pro_lifetime` | `$29.99 (one-time)`| Non-consumable (Lifetime License) |

> [!IMPORTANT]
> **Product IDs are case-sensitive!** They must match the strings above exactly (`adblocker_pro_monthly`, `adblocker_pro_annual`, `adblocker_pro_lifetime`).

---

## 🛠️ Step 1: Merchant Account Verification (One-Time Setup)

To charge users for subscriptions and in-app products, your Google Play Console account must be linked to a Google Payments Merchant Account:

1. Open [Google Play Console](https://play.google.com/console).
2. On the left navigation drawer, go to **Settings** > **Developer account** > **Payment settings**.
3. If not already created, click **Set up a merchant account**.
4. Enter your business or personal banking details and tax information.

---

## 📦 Step 2: Upload an AAB/APK with Billing Permission (Prerequisite)

> [!NOTE]
> Google Play Console **will not allow creating subscriptions or in-app products** until at least one APK/AAB declaring `com.android.vending.BILLING` has been uploaded to an active track.

1. In your project, generate a signed App Bundle:
   ```powershell
   .\gradlew.bat bundleRelease
   ```
   (File location: `app/build/outputs/bundle/release/app-release.aab`)
2. In Google Play Console, go to **Testing** > **Internal testing**.
3. Create a new release, upload `app-release.aab`, and click **Save & Review Release** > **Start rollout to Internal testing**.

---

## 💳 Step 3: Create In-App Products & Subscriptions

### 3.1 Create the Annual Subscription (`adblocker_pro_annual`)
1. In Google Play Console, navigate to **Monetize with Play** > **Products** > **Subscriptions**.
2. Click **Create subscription**.
3. **Product ID:** Enter `adblocker_pro_annual`
4. **Name:** `AdBlocker Pro (Annual)`
5. Click **Create**.
6. **Set up Base Plan:**
   * Click **Add base plan**.
   * **Base plan ID:** `annual-base`
   * **Type:** Auto-renewing
   * **Billing period:** 1 Year
   * **Grace period:** 16 days
   * **Set Price:** Enter `$14.99` (Play Console will auto-convert to local currencies).
   * Click **Save** > **Activate base plan**.
7. **Add 7-Day Free Trial (Optional but Recommended):**
   * Under the base plan, click **Add offer**.
   * **Offer ID:** `7-day-free-trial`
   * **Eligibility:** New customers only
   * **Phases:** Free trial for 7 days
   * Click **Save** > **Activate offer**.

---

### 3.2 Create the Monthly Subscription (`adblocker_pro_monthly`)
1. Under **Subscriptions**, click **Create subscription**.
2. **Product ID:** Enter `adblocker_pro_monthly`
3. **Name:** `AdBlocker Pro (Monthly)`
4. Click **Create**.
5. **Set up Base Plan:**
   * Click **Add base plan**.
   * **Base plan ID:** `monthly-base`
   * **Type:** Auto-renewing
   * **Billing period:** 1 Month
   * **Grace period:** 7 days
   * **Set Price:** Enter `$1.99`
   * Click **Save** > **Activate base plan**.

---

### 3.3 Create the Lifetime License (`adblocker_pro_lifetime`)
1. In the left navigation, go to **Monetize with Play** > **Products** > **In-app products**.
2. Click **Create product**.
3. **Product ID:** Enter `adblocker_pro_lifetime`
4. **Name:** `AdBlocker Pro Lifetime License`
5. **Description:** `Permanent lifetime access to all AdBlocker Pro features across all devices.`
6. **Status:** Active
7. **Price:** Enter `$29.99`
8. Click **Save** and then click **Activate**.

---

## 🧪 Step 4: Configure License Testing (Free Sandbox Purchases)

Google Play provides a sandbox environment so you can test purchases with a virtual card without real money being deducted.

1. In Google Play Console, go to **Setup** > **License testing**.
2. In the **License testers** field, add your personal Gmail address and any test team emails (separated by commas).
3. Under **License response**, select `RESPOND_NORMALLY`.
4. Click **Save changes** at the bottom right.
5. On your Android test device / emulator:
   * Open the app while signed into the tester Google account.
   * Tap **GO PRO** or any Pro feature.
   * The Google Play purchase dialog will show: *"Test card, always approves"*.
   * Complete the purchase to verify instant unlock!

---

## 🔄 Step 5: In-App Developer Testing (Available Now)

Even before setting up your Play Console products, the app includes an immediate **Dev Sandbox Switch**:
1. Open the app on your emulator or phone.
2. Tap the **GO PRO** pill at the top right of the Home screen or navigate to **Settings** > **Account**.
3. In the Pro Paywall bottom sheet, scroll down to the bottom.
4. Tap **`Dev: Unlock Pro`**.
5. The app will immediately toggle between **Free** and **Pro Annual** tiers, updating:
   * The **`PRO`** badge on the Home top bar.
   * The **`Account Tier: Pro Plus`** card on Account Screen.
   * Full uncapped access to Google Drive Cloud Sync, 30+ filter lists, and custom DoH resolvers!
