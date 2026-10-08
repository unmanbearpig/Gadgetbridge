# Gadgetbridge heart rate percentiles and OLED dashboard

Date: 2026-10-08
Upstream: https://codeberg.org/Freeyourgadget/Gadgetbridge
Starting revision: 12f7f372cb5e93369b8f7dc567c250f5c2f2e7ec

## Requested changes

- Fork the current Gadgetbridge source and keep useful upstream history.
- Show heart rate percentiles in daily, weekly, and monthly charts and statistics.
- Make recorded data easy to export. Audit existing export options first.
- Replace the first two dashboard rings with useful charts.
- Replace semicircular metric gauges with linear bars.
- Use high contrast, pure black OLED backgrounds, and restrained styling inspired by Slopalytics and the local job-search app.
- Implement, build, and run in an Android emulator when available.
- Commit changes as work progresses and record results here.

## Design

The dashboard is a compact health workspace with black surfaces, white numbers, cyan chart accents, and clear gray dividers. Charts carry the main visual weight.

Content order: date and device controls, heart rate and activity charts, compact metric rows, existing navigation and data actions. Keep labels short and omit explanatory subtitles.

Interactions: preserve native date navigation and chart selection, keep metric taps opening detailed charts, and use short native transitions without decorative animation.

Use the existing Android UI stack and chart library. Preserve device support and data storage. Dark styling should also cover chart labels, navigation, dialogs, and empty states.

## Heart rate and export behavior

- Report P5, P25, P50, P75, and P95 in bpm, with valid sample count.
- Calculate sample-based percentiles using linear interpolation on sorted valid observations. Exclude device sentinel values and missing readings according to existing heart rate validity rules.
- Daily statistics use the selected local day. Weekly and monthly statistics preserve Gadgetbridge's existing trailing 7-day and 30-day ranges ending on the selected local day and expose daily percentile trends where the existing chart structure permits.
- Empty periods show an empty state, never fabricated zero bpm statistics. Keep gaps in time series.
- Provide CSV export from the health charts with timestamps and recorded values, plus percentile summaries for the same selected range. Retain existing database/ZIP and workout exports and make the full export screen easy to reach.
- Use Android's document picker and background work for file creation. Avoid requiring broad storage permissions.

## Work checklist

- [x] Inspect host tools and styling references.
- [x] Clone active Codeberg upstream into the fitness workspace.
- [x] Create GitHub fork and configure remotes.
- [x] Commit this plan and create an implementation worktree.
- [x] Audit dashboard widgets, heart rate providers, date ranges, and existing exports.
- [x] Add tested percentile calculation and daily/weekly/monthly presentation.
- [x] Add convenient recorded-data CSV export and export navigation.
- [x] Replace overview rings with charts and gauges with linear bars.
- [x] Apply OLED theme and high contrast chart styling.
- [x] Build the debug APK and run focused tests and required checks.
- [x] Install and verify in an emulator using clearly identified test data.
- [x] Record screenshots, validation results, and any limitations.
- [x] Merge committed work back to the main checkout and push the fork.

## Phone data migration

- [ ] Export and validate a full ZIP backup from the original app.
- [ ] Build and install a separate `Gadgetbridge Fitness` app.
- [ ] Restore the backup and verify database records, device settings, and charts.

2026-10-08: The connected phone runs the official Gadgetbridge 0.94.0. Added a `fitness` flavor with package ID `nodomain.freeyourgadget.gadgetbridge.fitness`, a distinct Pebble provider authority, and the `Gadgetbridge Fitness` label so the fork can coexist with the original app. Migration uses Gadgetbridge's full ZIP backup, including database, preferences, device settings, and external files. Personal backups and verification files stay in ignored local build directories.

## Progress

2026-10-08: Cloned current upstream. GitHub's upstream mirror is archived, so the fork will use the current Codeberg source. Found Android SDK platforms through API 37, existing emulator images, a Java 21 toolchain, and KVM. Both styling references use DM Sans, pure black backgrounds, bright text, sparse borders, and chart-first layouts. The local job-search theme supplies the cyan accent `#8bdcfb` and muted text `#b5becb`.

## Validation record

The final mainline debug build, 21 focused tests, and Android lint passed. Lint reports 1,424 existing warnings and uses upstream's existing baseline. No new error suppressions or baseline entries were added.

Validation command, with Java 21 and `ANDROID_HOME=/home/unmbp/Android/Sdk`:

```bash
./gradlew :app:assembleMainlineDebug :app:testMainlineDebugUnitTest \
  --tests '*HeartRatePercentilesTest' --tests '*HealthCsvExporterTest' \
  --tests '*HeartRateWidgetTest' --tests '*WidgetLayoutStoreTest' \
  --tests '*GaugeDrawerTest' --tests '*HeartRatePeriodFragmentTest' \
  :app:lintMainlineDebug --console=plain
```

Final combined verification completed in 3m 37s. The APK was installed and run on the dedicated `gadgetbridge-fitness` API 31 emulator at `emulator-5560`. Verified day/week/month tabs, both CSV exports through the document picker, export state across picker rotation, and dashboard access to the existing database/ZIP export screen. Monthly raw CSV sorting, sample count, and all five percentiles agree with the screen and summary CSV.

Screenshots use Gadgetbridge's built-in synthetic test device. They do not contain personal health data:

- [Dashboard](evidence/2026-10-08-gadgetbridge/dashboard.png)
- [Daily HR](evidence/2026-10-08-gadgetbridge/hr-day.png)
- [Weekly HR](evidence/2026-10-08-gadgetbridge/hr-week.png)
- [Monthly HR](evidence/2026-10-08-gadgetbridge/hr-month.png)
- [Synthetic monthly percentile export](evidence/2026-10-08-gadgetbridge/synthetic-month-percentiles.csv)

No physical wearable was paired during this task. Percentiles describe recorded samples, so devices with different recording frequencies can produce different sample distributions. Week and month preserve upstream's rolling 7/30-day windows. Explicit existing theme selections remain available; new installs default to dark OLED with cyan accents.

The generated debug APK uses upstream's application ID and the local debug signing certificate. Build outputs and reports are local artifacts, not committed binaries.

To reopen the emulator, run `/home/unmbp/Android/Sdk/emulator/emulator -avd gadgetbridge-fitness`. The test fixture and installed APK remain in that profile.

2026-10-08: Created https://github.com/unmanbearpig/Gadgetbridge and configured Codeberg as `upstream`. Committed the plan as `09a4172bc` and created the `feature/hr-percentiles-oled-dashboard` worktree. Initial mainline debug build passed in 5m 52s. Dedicated `gadgetbridge-fitness` API 31 emulator booted with KVM. Existing exports include database/preferences backup, ZIP backup, and GPX/FIT workout export. Added selected-range UTF-8 sample CSV and daily/range percentile CSV via Android's document picker. Added percentile statistics and trends, chart widgets with saved-layout migration, and shared linear gauge rendering. Fixed dashboard day boundaries to start at local midnight. Validation of the changed app is underway.

2026-10-08: Changed app assembles successfully. First focused test run passed all percentile, CSV, and layout tests. Five rendering tests exposed missing theme attributes in application-only test contexts; added gauge track fallbacks and explicit text coloring during binding. Pixel tests now use Robolectric native graphics. Dark/OLED/cyan are defaults, while explicit theme selections remain available. Revalidating these fixes.

2026-10-08: All 21 focused tests passed. Verified daily, weekly, and monthly charts on the emulator. Exported monthly summary CSV through the system document picker: 30 daily rows plus one range row. The range contains 36,736 valid observations with P5/P25/P50/P75/P95 of 51/56/72/81/108 bpm, exactly matching the screen. Raw sample CSV also contains 36,736 synthetic records and reproduces the same five percentiles. The built-in test provider intentionally omits some minutes; no readings were filled in.

2026-10-08: First lint run found four pre-existing upstream errors: missing Bluetooth permission handling in Oppo and Sennheiser discovery/bond checks, and two API-28-only line-height attributes in styles shared with API 23. Added denial handling and switched to AppCompat line-height attributes. Compacting HR statistics into plain three-column rows, fitting percentile trends to their actual plotted range, and aligning the bottom date bar with the selected 7/30-day period before final verification.

2026-10-08: Final emulator review passed for the compact statistics, all three percentile tabs, cyan linear bars, and black surfaces. Verified daily export after rotating the document picker. Saved screenshots and synthetic export evidence under `plans/evidence/2026-10-08-gadgetbridge/`. All source changes are committed; merging and pushing the fork is the remaining step.

2026-10-08: Fast-forwarded the main checkout and pushed the implementation to both `master` and `feature/hr-percentiles-oled-dashboard` on the GitHub fork. Preserved the [debug APK](../app/build/outputs/apk/mainline/debug/gadgetbridge-fitness-debug.apk), test reports, and lint reports in the main checkout. APK SHA-256: `440ceee89c537adeb27c6f0bd9eb4293d9438b7cc98891269d59658d6d667bd6`. Shut down the task emulator; its profile retains the installed app and synthetic fixture for reopening.
