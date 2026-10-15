# Myanmar Hazard Guard — Android (မြန်မာ သဘာဝဘေး သတိပေး)

Android 6.0+ (API 23+) Java application for Myanmar residents with **Burmese ↔ English**, earthquake listings, NOAA ENSO index tracking, weather, model-based air quality, and device-local notifications.

## What it does

- **Earthquakes:** USGS regional reported events (Myanmar + nearby areas; latitude 9–29°, longitude 90–104°), 48-hour feed, 80 most recent records. Triggers a new-event notification for **newly detected** reports meeting a chosen threshold (default M4.5), **only if the reported event is less than 30 minutes old at a successful check**. Notifications are *post-detection*; no earthquake prediction.
- **El Niño / La Niña:** Reads NOAA CPC's **Relative Oceanic Niño Index (RONI)** HTML table. In February 2026 NOAA adopted RONI for operational ENSO monitoring. Shows latest three-month season and an **index-only sustained warm/cool pattern** when ≥5 consecutive values meet ±0.5°C. NOAA official event/outlook requires additional scientific assessment. Climate-trend notifications are **informational**, not emergency alarms.
- **Air quality:** Open-Meteo `current=us_aqi,pm2_5,pm10` (modeled estimates), alerts for US AQI ≥151 with six-hour per-city cooldown.
- **Weather:** Open-Meteo current conditions plus up to 6 hours of forecast rain/wind; advisory alerts for estimated rainfall ≥15 mm/h or wind ≥60 km/h, six-hour per-city cooldown. These are not government-issued weather warnings.
- **Notifications:** configurable sound, opt-in loud alarm tone, vibration, and local alert history. Android 13+ asks for notification permission; OS-level channel muting may override the in-app selection.
- **Voice:** Android TextToSpeech for foreground urgent alerts and an accessible test. Burmese TTS requires a compatible voice installed on the phone; some engines fall back to English. **No guaranteed narration while the app is closed.**
- **Cities:** 14 Myanmar cities; city change clears location-dependent weather/air cache.
- **Checks:** Android `JobScheduler` requests network-connected background work about every 15 minutes. **Real execution can be delayed, suppressed, or batched** by Doze, App Standby, OEM battery management, connectivity problems, or permissions. It is not a low-latency official alert service.
- **No account, API keys, GPS, or ads.** Internet is necessary for fresh data. Last fetched results remain cached on-device, and the app doesn't invent readings if no provider has responded.

## Build an actual APK on Windows

This zip contains a complete Android Studio Gradle project, **not an already-built APK**. The execution environment used to prepare this project had no Android SDK/Android build tools and no network access; **the APK could not be compiled or device-tested here**. Never rename the zip to `.apk`.

### Option 1 — Android Studio (recommended)

1. Install **Android Studio** from https://developer.android.com/studio (use its embedded JDK 17+).
2. Unzip the entire project folder and select **Open** in Android Studio.
3. Install Android SDK Platform **35** (Android 15) and Android SDK Build Tools from **SDK Manager** if prompted.
4. Allow Gradle Sync to download the official Gradle 8.11.1 distribution and Android Gradle Plugin 8.9.1. The included wrapper bootstraps Gradle on first use if missing.
5. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)** or open the terminal in the project folder and run `gradlew.bat assembleDebug`.
6. Find `app\build\outputs\apk\debug\app-debug.apk`, copy it to your Android phone, open the file and approve its install prompt. Debug builds are for personal testing and are signed with Android's default debug key.

### Option 2 — Windows helper

Install Android Studio/SDK, ensure `JAVA_HOME` points to JDK 17+ or its Java is on PATH, then double-click **`BUILD_APK_WINDOWS.bat`**. It builds and opens the APK output location. Requires internet for the first Gradle sync. If Gradle cannot see Android SDK, create `local.properties` containing an `sdk.dir` path such as `sdk.dir=C:\\Users\\YOU\\AppData\\Local\\Android\\Sdk` (adjust path and escape backslashes), or set `ANDROID_HOME`.

### Option 3 — GitHub Actions

Push the project contents into a private/new GitHub repository, open **Actions → Build Android APK → Run workflow**. Download artifact **Myanmar-Hazard-Guard-APK** from the successful workflow. `.github/workflows/apk.yml` is included. This is useful if your Windows PC cannot run Android Studio.

## Known limitations and safer next steps for production

**IMPORTANT: This is a starting version for testing, not a certified life-safety system.** It has not been built or field-tested in this environment. Do not rely on it as your only safety information. **No phone app can guarantee an earthquake early warning solely by polling USGS reports.** Earthquake early warning depends on dedicated seismic networks and official services; observed quake notices often arrive after shaking. Weather and AQI numbers are model estimates. ENSO is a seasonal climate pattern, not a minute-by-minute disaster event.

Production-grade delivery would require field testing on different OEM devices, a monitored alert backend (e.g., secure push notifications) integrated with **authorized local warning sources**, compliance/privacy review, security audits, clear supported locales, accessibility review, localization QA by native Burmese speakers, robust NOAA parser maintenance, telemetry of failed checks (with informed user consent), and an appropriate release-signing key. The sample RONI parser depends on NOAA's current HTML table and may need updating if NOAA changes the site.

Android OS permissions and channel configuration can override app preferences. Notification history is stored locally in SharedPreferences and is not encrypted. The public data fetcher sends the selected city coordinates to Open-Meteo, *not precise GPS coordinates*. There is no location permission. Fonts and TextToSpeech voices depend on device and installation.

## Data credits / endpoints

- USGS Earthquake Hazards Program API: https://earthquake.usgs.gov/fdsnws/event/1/ (post-detection event data)
- NOAA CPC RONI: https://www.cpc.ncep.noaa.gov/products/analysis_monitoring/enso/roni/
- NOAA RONI notice: https://www.cpc.ncep.noaa.gov/products/analysis_monitoring/enso/roni/announcement.php
- Open-Meteo forecast: https://open-meteo.com/en/docs
- Open-Meteo air quality: https://open-meteo.com/en/docs/air-quality-api

This project is provided as editable source code. Validate API attribution/licensing for your distribution, usage volume, and planned commercial use before publishing to a public app store.
