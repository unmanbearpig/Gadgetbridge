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
- [ ] Build the debug APK and run focused tests and required checks.
- [ ] Install and verify in an emulator using clearly identified test data.
- [ ] Record screenshots, validation results, and any limitations.
- [ ] Merge committed work back to the main checkout and push the fork.

## Progress

2026-10-08: Cloned current upstream. GitHub's upstream mirror is archived, so the fork will use the current Codeberg source. Found Android SDK platforms through API 37, existing emulator images, a Java 21 toolchain, and KVM. Both styling references use DM Sans, pure black backgrounds, bright text, sparse borders, and chart-first layouts. The local job-search theme supplies the cyan accent `#8bdcfb` and muted text `#b5becb`.

## Validation record

Pending implementation. Verify percentile edge cases, selected calendar ranges, CSV correctness, zero/missing data, chart navigation, and dashboard layout on a running emulator. Record real commands and outcomes here.

2026-10-08: Created https://github.com/unmanbearpig/Gadgetbridge and configured Codeberg as `upstream`. Committed the plan as `09a4172bc` and created the `feature/hr-percentiles-oled-dashboard` worktree. Initial mainline debug build passed in 5m 52s. Dedicated `gadgetbridge-fitness` API 31 emulator booted with KVM. Existing exports include database/preferences backup, ZIP backup, and GPX/FIT workout export. Added selected-range UTF-8 sample CSV and daily/range percentile CSV via Android's document picker. Added percentile statistics and trends, chart widgets with saved-layout migration, and shared linear gauge rendering. Fixed dashboard day boundaries to start at local midnight. Validation of the changed app is underway.

2026-10-08: Changed app assembles successfully. First focused test run passed all percentile, CSV, and layout tests. Five rendering tests exposed missing theme attributes in application-only test contexts; added gauge track fallbacks and explicit text coloring during binding. Pixel tests now use Robolectric native graphics. Dark/OLED/cyan are defaults, while explicit theme selections remain available. Revalidating these fixes.
