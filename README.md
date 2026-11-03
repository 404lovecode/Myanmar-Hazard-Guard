# Myanmar Hazard Guard / HazardGuard World v4

An Android companion for disaster information in Myanmar and worldwide. **Not an official emergency warning or dispatch service.**

## Changes in v4
- Cozy accessible sage/cream UI, warmer dark mode, a new shield-and-heartbeat launcher icon.
- Always-visible Emergency assistance shortcut. Shows country-specific published phone numbers based on your chosen city or opted-in location, allows manual country selection, and opens the Android dialer with a tap. No automatic calls or CALL_PHONE permission.
- Country numbers for Myanmar, Thailand, India, Bangladesh, Singapore and Malaysia with in-app source links; unknown countries show **no guessed number**.
- Nearby hospital map search uses the selected location (which may not equal actual location).
- GDACS now tries the official last-seven-days GeoJSON API, followed by official RSS feeds. Keeps successful cached reports on failure and displays source freshness/status. Manual refresh retries.
- Existing USGS, NOAA, EONET, Open-Meteo and multilingual features retained from v3.2.

## Development and safety
The Android debug APK builds in GitHub Actions from the **checked-in source code**, not a legacy archive overlay. Build requirements: JDK 17, SDK 35, Gradle 8.11.1, AGP 8.9.1.

Emergency numbers are published by UK FCDO: https://www.gov.uk/foreign-travel-advice/myanmar/getting-help , and equivalent country getting-help pages. Before depending on the app, verify numbers with local organizations. No app can guarantee response time, continuous network connectivity, precise position, or emergency-call success.

GDACS is credited to the Global Disaster Alert and Coordination System. See https://www.gdacs.org/feed_reference.aspx and https://www.gdacs.org/.
