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


## v5.0 — World headlines and 72-hour preparedness

- Dedicated World News screen linked from Home and World. The existing GDACS disaster
  bulletins remain **separate** from general news.
- Public Google News RSS headlines with a **72-hour publication window** and timestamp
  labels, from World, Protests & civic events, Disasters, Food & humanitarian,
  Health, Technology, and Climate.
- Choose from 23 specific editions/languages or a curated **8-edition multilingual mix**.
  Headlines display in their publisher's language; no automatic translation or claim
  that every language in the world is available. Articles open in the user's browser,
  which may offer translation depending on the browser.
- Refresh and 10-minute local cache, offline/stale warnings, HTTPS-only links and
  bounded RSS XML parsing. No account, news API key or payments.
- 72-hour preparedness checklist for water, non-perishable food, medicines, light,
  communications, documents and household-specific needs. Based on
  https://www.ready.gov/kit. This checklist is preparation, **not** a claim of an
  imminent emergency.
- News coverage and language availability depend on public feeds, network access
  and publisher editions. Headlines are **not** verified official emergency alerts.
- No protest prediction, location tracking for news, automated calls or social
  surveillance. Emergency dialer remains user-initiated.


## v6.0 — one-tap World News translation

- World News now has an independent **Translate articles to** language selector.
  It follows the chosen news/app edition unless the reader overrides it.
- Every eligible story retains the publisher's original-language headline and offers:
  **Translate headline**, **Read translated article**, and **Read original**.
- Translation opens Google Translate's public web interface on demand. It supports
  Burmese (Myanmar) and other target languages without any translation API key,
  account or subscription. An Internet connection is required.
- Some news sites cannot be translated by third-party website translation due to
  restrictions. The original article is always available separately.
- Headlines and URLs are shared with Google Translate **only if the user taps** one
  of the translation options. The app does not silently forward news to translation.
- Translated text is machine generated and may contain errors, especially for
  emergency safety instructions. Verify urgent information with official sources.
- This is **not** automatic inline/offline translated headlines. Google ML Kit's
  offline translator currently does not support Burmese; this feature uses a
  user-initiated external translation website instead.

## Technical notes
The translated headline link is URL encoded and capped in length.
Original story links must be valid HTTPS URLs. Unit checks run during the
GitHub Actions build before packaging.
