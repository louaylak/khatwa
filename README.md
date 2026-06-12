# Khatwa (خطوة)

A Strava-style GPS activity tracker for Android — built for Algeria, with a fully
**offline OpenStreetMap of the whole country** (every street, side road and footpath),
multiple profiles with personalized calorie models, and a dark "night asphalt + Saharan
ember" design.

- Walk · Run · Bike
- Live distance, moving time, pace/speed, elevation gain, calories
- Auto-pause when you stop, GPS noise filtering, elevation smoothing
- Per-km splits with fastest-split highlight
- History with route maps and animated route draw-in
- Elevation + pace (or speed) charts per activity
- Weekly / monthly stats dashboard
- Background tracking with a live notification (screen off, app minimized)

---

## 1. Get the APK without installing anything (recommended)

GitHub builds the app for you, for free. One-time, ~10 minutes, no Android Studio:

1. Create a free account at **github.com** (if you don't have one).
2. **New repository** → name it `khatwa` → Public → Create.
3. On the empty repo page, click **"uploading an existing file"** and drag in
   **everything that is INSIDE this Khatwa folder** (the `app`, `gradle` and
   `.github` folders plus all the root files like `settings.gradle.kts`).
   ⚠️ Upload the *contents*, not the Khatwa folder itself — `settings.gradle.kts`
   must end up at the repo root. Commit.
4. Check the file list shows `.github/workflows/build-apk.yml`. If your browser
   skipped it: **Add file → Create new file**, type
   `.github/workflows/build-apk.yml` as the name and paste that file's content.
5. Open the **Actions** tab → the "Build APK" run turns green in ~5–8 min.
6. On your phone, open the repo → **Releases** → download **Khatwa.apk** →
   open it → allow "install from unknown sources" → installed.

Every future push rebuilds the APK automatically (or run it manually from the
Actions tab via "Run workflow").

## 2. Build it yourself (Android Studio)

This is a complete Android Studio project. You cannot install it directly — you build
the APK once on your PC, then it lives on your phone like any app.

1. Install **Android Studio** (free, https://developer.android.com/studio).
2. **File → Open** → select this `Khatwa` folder.
3. Wait for the first Gradle sync (it downloads Gradle 8.7 + all libraries
   automatically — needs internet, takes a few minutes the first time).
4. Enable **USB debugging** on your phone
   (Settings → About phone → tap *Build number* 7× → Developer options → USB debugging).
5. Plug in the phone, pick it in the device dropdown, press **Run ▶**.

To share the app without Android Studio: **Build → Generate Signed App Bundle / APK → APK**,
then send the `app-release.apk` to any phone.

Requirements: `minSdk 26` (Android 8.0+), `targetSdk 34`, JDK 17 (bundled with Android Studio).

## 2. The offline map

On first launch the app offers a one-time download of **`algeria.map` (~277 MB)** —
OpenStreetMap data in mapsforge format, rendered entirely on the phone. After that,
**zero internet is needed**, including all side roads and footpaths.

- The download **resumes** automatically if interrupted (HTTP Range).
- You can also download the file on a PC and sideload it: get
  `https://download.mapsforge.org/maps/v5/africa/algeria.map`
  (or the Esslingen mirror), copy it to the phone, and use
  **"Import algeria.map from storage"** on the setup screen.
- "Use online maps for now" falls back to standard OSM tiles (needs internet).
- The map screen shows an `OFFLINE MAP` / `ONLINE TILES` pill so you always know which
  renderer is active.

Map data © OpenStreetMap contributors.

## 3. How calories are computed (documented for honesty)

Per-profile **BMR** via **Mifflin–St Jeor**:
`BMR = 10·kg + 6.25·cm − 5·age + s` (s = +5 male, −161 female).

Energy per minute uses the **corrected METs** convention:
`kcal/min = MET × BMR / 1440` (so 1 MET = resting burn for *this* person, not the
generic 3.5 ml/kg/min).

- **Walking** — ACSM equation: `VO₂ = 3.5 + 0.1·S + 1.8·S·G` (S in m/min, G = grade).
- **Running** — ACSM: `VO₂ = 3.5 + 0.2·S + 0.9·S·G`, with a smooth walk→run blend
  between 6.5–8.5 km/h, grade clamped to [−8 %, +15 %], VO₂ floor 4.0.
  METs = VO₂ / 3.5.
- **Cycling** — Compendium speed-banded METs, plus climbing work
  `m·g·Δh / 0.22 / 4184` (≈22 % gross efficiency) added on ascents.

GPS hygiene: fixes worse than 25 m accuracy are dropped (30 m bike), teleport jumps
rejected, speed is EMA-smoothed (α = 0.35), elevation passes a 7-sample median filter
with 2.5 m hysteresis before gain/loss is counted, auto-pause below ~0.4 m/s.
Activities shorter than 30 m / 30 s are not saved.

## 4. Samsung / aggressive battery savers

Android kills background apps to save battery, which freezes tracking when the screen
is off. The app asks once for the battery-optimization exemption; if you skipped it:

**Settings → Apps → Khatwa → Battery → Unrestricted** (Samsung)
and keep **Location → Allow all the time** if offered.

## 5. Honest limitations

- **Elevation gain comes from GPS altitude.** Even with median filtering it will not
  match Strava exactly (Strava corrects against an elevation basemap). Expect the same
  *shape*, not the same number.
- If Android **force-kills** the process mid-activity, the in-progress recording is
  lost (the service is `START_NOT_STICKY` by design — no zombie half-tracks).
- Calorie figures are physiological *estimates*; treat them as consistent, not exact.
- The first GPS fix outdoors can take 10–60 s; the GPS pill shows live accuracy.

## 6. Project map (where to change things)

```
app/src/main/java/com/khatwa/app/
├── KhatwaApp.kt            osmdroid/mapsforge init, notification channel
├── MainActivity.kt         navigation graph + bottom tabs
├── data/Data.kt            models, JSON persistence, profiles, prefs
├── map/OfflineMap.kt       map download/resume/import  ← map URL here
├── tracking/Calc.kt        calories, GPS filters, elevation filter ← formulas here
├── tracking/TrackingService.kt   foreground GPS service
├── tracking/TrackingManager.kt   live state shared with UI
├── ui/Theme.kt             colors ← change the palette here
├── ui/Maps.kt              osmdroid wrapper, offline tile switch
├── ui/Components.kt        charts, pulse button, shared widgets
└── ui/Screen*.kt           Setup / Profiles / Record / History / Stats
```

- Rename the app: `app/src/main/res/values/strings.xml`.
- App icon: `res/drawable/ic_launcher_foreground.xml` + `values/ic_launcher_background.xml`.

## 7. Troubleshooting builds

- **mapsforge version conflict** during sync: in `app/build.gradle.kts` try bumping the
  three `org.mapsforge` artifacts to `0.21.0`, or delete them and let
  `osmdroid-mapsforge` pull its own.
- **"Could not find gradle-wrapper"**: open the project folder itself (the one with
  `settings.gradle.kts`), not a parent folder.
- First sync is slow on weak connections — let it finish once; later builds are fast.
