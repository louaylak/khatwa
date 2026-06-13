# Khatwa (خطوة)

A Strava-style GPS activity tracker — now worldwide on **MapLibre + OpenFreeMap** (free, no API key), with a
violet "night asphalt + electric purple" design, multiple profiles with
personalized calorie models, and AdMob interstitials.

- Walk · Run · Bike, anywhere in the world
- Live distance, moving time, pace/speed, **real-time GPS elevation**, calories
- Auto-pause, GPS noise filtering, elevation smoothing (elevation feeds the calorie model)
- Per-km splits, history with route maps, elevation + pace charts
- Weekly / monthly stats + **daily maintenance calories** (BMR × activity level)
- Background tracking with a live notification
- Animated 2D game-style characters (Aiko / Kenji, by profile gender): sprint intro on the
  welcome screen, your runner moves on the map with you at real GPS speed, and sits
  exhausted next to your results when you finish
- Country selection on first launch
- Ads: one interstitial at app open, one after finishing an activity — never on Start

---

## 1. The map — MapLibre + OpenFreeMap (free forever)

The map is **MapLibre** (open source) rendering **OpenFreeMap** tiles:
no API key, no billing, no quotas, worldwide coverage, modern clean look.
Style URL lives in `ui/Maps.kt` (`liberty`; alternatives: `bright`, `positron`).
Attribution (© OpenStreetMap / OpenFreeMap) is shown automatically — required, keep it.

## 2. AdMob — read this before publishing

- The AdMob **App ID** (`ca-app-pub-6787264530091827~5941720252`) is already wired
  in the manifest.
- Ad placements: app open (skippable, max ~4.5 s wait on bad networks, never blocks)
  and after **Finish & Save** (then the summary opens). Starting an activity never
  shows an ad. If an ad fails to load, the app continues instantly.
- **`ui/Ads.kt` has `USE_TEST_ADS = true`.** While testing on your own phone it must
  stay `true` (Google's sample ads show). Set it to `false` ONLY in the build you
  publish — clicking or repeatedly loading your own real ads gets the AdMob account
  limited or banned for invalid traffic.
- New ad units can take up to **1 hour** before real ads start serving.

## 3. Build / rebuild

GitHub Actions builds the APK on every push (see `.github/workflows/build-apk.yml`),
APK appears under **Releases**. Or open the folder in Android Studio and press Run.
Requirements: minSdk 26, compileSdk 34, JDK 17.

## 4. Calories (documented)

Per-profile **BMR** via Mifflin–St Jeor; corrected METs (`kcal/min = MET × BMR/1440`);
ACSM walk/run equations **including grade from live GPS elevation**; Compendium
cycling METs + climbing work. **Maintenance** = BMR × activity level
(1.2 sedentary → 1.725 very active), shown in the profile editor and Stats tab.
Height and weight both accept decimals (e.g. 177.5).

## 5. Honest limitations

- Map tiles and ads need **internet**; GPS tracking itself still works offline,
  the route draws over a blank map until connection returns.
- GPS elevation is filtered but is not survey-grade — same shape as Strava,
  not the same number.
- If Android force-kills the process mid-activity, that recording is lost.

## 6. Where to change things

```
ui/Theme.kt        colors (violet palette)
ui/Ads.kt          ad unit ids + USE_TEST_ADS switch
ui/Maps.kt         follow zoom level (FOLLOW_ZOOM = 16f)
tracking/Calc.kt   all calorie/GPS math
AndroidManifest    Maps API key + AdMob App ID
```
