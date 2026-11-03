# HazardGuard World v3.0 (Android)

Updated worldwide disaster information app with a user-selected local area, a world events explorer and worldwide GDACS disaster bulletins. Preserves Myanmar and Burmese support.

## New in v3

- **Location:** Tap Settings → Use my phone location (or the location chooser). Android asks for approximate location **when you tap the action**; no background GPS tracking or ongoing coordinates collection. The app uses the system geocoder when available to identify a city and country; if unavailable, you can pick a city manually. Location is stored locally and coordinates are used for USGS/Open-Meteo queries.
- **Worldwide disaster news:** GDACS seven-day disaster RSS feed displayed at the **top of World**, updated on manual refresh / scheduled refresh subject to network availability, with a 30-minute cache. Linked original bulletin titles and dates. Not general world political/economic news; not a push-warning feed. Requires internet.
- **Automatic UI language:** Based on country associated with selected/detected place, only among supported translations. Supports English, Burmese, Thai, Spanish, French, Hindi, Indonesian. Manual language override in Settings, plus an Automatic option. Nonmatching countries fall back to English. Existing long descriptions/alert messages are fully localized only for English and Burmese; some untranslated strings fall back to English in other languages. Source news headlines are not automatically translated.
- **Appearance:** System / Light / Dark, saved across sessions. Theme-aware headers, cards, contrast, status/navigation bars and Android dialogs.
- **Internet indicator:** Home indicates a network-capable connection; this does not guarantee any specific provider is reachable. Individual feeds can fail and cached data can be shown with timestamps.

## APK building

See `GITHUB_UPDATE_INSTRUCTIONS.txt` or build with JDK 17, SDK Platform 35 and Gradle `assembleDebug` in Android Studio. The distributed update ZIP is flat-root and meant to be uploaded **as one file** to your existing GitHub repository with the matching workflow update. The project directory in the full-source archive is already an Android Gradle project.

## Public data providers

- GDACS public XML feed: https://www.gdacs.org/feed_reference.aspx
- USGS: https://earthquake.usgs.gov/fdsnws/event/1/
- Open-Meteo: https://open-meteo.com/en/docs
- NASA EONET: https://eonet.gsfc.nasa.gov/docs/v3
- NOAA RONI: https://www.cpc.ncep.noaa.gov/products/analysis_monitoring/enso/roni/

## Safety

This app is not an official emergency alert platform. Earthquakes cannot be predicted. Model forecasts, news feeds, satellite event listings and locally scheduled Android notifications can all be late, incomplete or unavailable. World news bulletins do not trigger sound/vibration alarms. Follow local government and emergency services for life-safety warnings.

## Build validation

Syntax parsing of 14 Java files, XML resource/manifest validation and resource-name consistency completed. An Android SDK build and real-device testing **have not** been performed in this environment, so compilation and runtime functionality are not verified. GitHub compilation and an actual phone test are necessary.


## v3.1 network diagnosis and source statuses
See RELEASE_NOTES_V3_1.txt and the accompanying GitHub instructions.


## v3.2: Human-readable event details
Tap a NASA EONET tracked-event card on World to open in-app details, not raw API JSON. Optional map and source-report links are clearly labeled. The app remains supplementary information only.
