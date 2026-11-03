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


## v7.0 — AUTOMATIC translation INSIDE Hazard Guard

- World News now **auto-translates up to 22 visible headlines** into the
  reader's chosen app language (default) or their separate translation language.
  English, mixed-language, Myanmar/Burmese and other supported editions are
  independent of the reader's preferred translation language.
- Translations are shown **directly in each World News card** (with the
  original-language headline also visible), not via Google Translate links
  or a separate app. The switch "Auto-translate headlines" allows opting out.
- A **Read in app** button launches a safe, integrated WebView article reader.
  It attempts to show the full publisher page through Google Translate's
  website translation proxy and offers an Original tab if that fails.
  Some publisher sites and regions block website translation; full article
  translation **cannot be guaranteed**. RSS has headlines, not full articles.
- When auto-translation is enabled, only **public headline text** is sent from
  the phone to MyMemory's documented free HTTPS endpoint. No personal location,
  contacts or chat content are sent. The provider may log these headlines.
- No API keys, account, credits or subscription. Free anonymous MyMemory usage
  is limited to approximately 5,000 characters/day per IP (including use by
  other devices sharing that IP), and 500 UTF-8 bytes per request. The app caps
  its own daily volume below that and caches successful translations for
  offline reading. Translation may fail due to quotas, poor language support,
  connectivity or provider changes: original text remains visible and a
  failure is never mislabeled as a successful translation.
- Translation accuracy varies, especially for Burmese and emergency-related
  content. **Never treat machine-translated news as an official warning.**
  Verify emergency guidance with authoritative primary sources.

References: https://mymemory.translated.net/doc/spec.php
https://mymemory.translated.net/doc/usagelimits.php
https://support.google.com/translate/answer/2534559?hl=en


## v8.0 — BBC Burmese, DVB Burmese and The Irrawaddy

- **Myanmar News (🇲🇲)** entry points are present on Home and World screens.
  Burmese-language app users get the Myanmar publishers feed by default.
- In World → Global News choose **News source**:
  World / All publishers, Myanmar News (three sources), BBC News မြန်မာ,
  DVB သတင်း, or ဧရာဝတီ / The Irrawaddy.
- BBC News Burmese uses the BBC's public `https://feeds.bbci.co.uk/burmese/rss.xml`.
  The Irrawaddy's Burmese edition uses `https://burma.irrawaddy.com/feed`.
  DVB Burmese currently serves its public home page, not a functioning RSS at
  `/feed`: this version extracts only dated, linked headlines from the
  public `https://burmese.dvb.no/` listing and links back to the original
  article. It deliberately does not guess missing dates. DVB listing date
  entries have *day precision only*, clearly marked in the UI.
- Headline cards credit each publisher and preserve the original Burmese text.
  The existing user-selected automatic in-app translation still works for
  English/non-Burmese headlines.
- All three sources are independent fetches. If one fails, reachable sources
  still show up, with a source-count warning. Previously cached reports remain
  available offline. The news feed is a **72-hour window**, not a real-time
  warning service or a guarantee of completeness.
- The news source selector is separate from worldwide topic and language
  controls; publisher-specific feeds show all recent articles rather than
  pretending to filter by unrelated worldwide categories.
- News is attributed, not endorsed or rewritten. No account, news API key or
  payment is required. Respect each publisher's terms for public feeds;
  article copyright remains with its publisher.

Publisher sites:
https://www.bbc.com/burmese
https://burmese.dvb.no/
https://burma.irrawaddy.com/
