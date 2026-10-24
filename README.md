# Grindhaus

A personal Android app for a Pixel 8 Pro: it remembers how each bean was dialed in, logs every brew with a taste rating, and estimates how much caffeine will still be in the body at bedtime. Everything stays on the phone.

**Status:** skeleton (build step 3 of 10). Three tabs, the caffeine engine with tests, and the first database table. The full spec lives in the "Grindhaus Spec v2" doc.

## Getting the app onto the phone

Every push to GitHub builds the app and runs the tests (the **Actions** tab). Each green build of `main` is published as a release with a plain `.apk` attached.

- **Easiest:** point [Obtainium](https://github.com/ImranR98/Obtainium) at this repository once. It installs each new release as an update.
- **By hand:** open **Releases** (or `/releases/latest`), tap the `.apk`, and install. Android asks once to allow installs from that source.

Every build is signed with the same committed debug key, so new builds install over old ones and keep the app's data. A red build never becomes a release.

## What is checked automatically

- Caffeine engine: absorption peak, half-life decay, summed doses, the derived bedtime limit (32 mg at a 5 h half-life), and the after-bedtime check that catches a late cup.
- Bedtime clock: after-midnight bedtimes and daylight-saving changes.
- Database: beans are archived, never deleted.
- Screens rendered on a simulated phone and saved as screenshots (the `screenshots` artifact on each run).

Not checked until it runs on the phone: camera, vibration, the home-screen widget, and how it feels in the hand.

## Project layout

```
app/src/main/java/com/padraik99/grindhaus/
  domain/   caffeine engine and bedtime clock (pure Kotlin, no Android)
  data/     Room database: beans
  ui/       Compose screens and theme
app/src/test/   unit, database and screenshot tests
app/schemas/    exported database schema, one file per version
```

## Building locally (optional)

Requires JDK 21 and the Android SDK. `./gradlew recordRoborazziDebug assembleDebug` (runs every test and saves screenshots to `app/screenshots/`).

## Sources for the caffeine numbers

- Gardiner C, et al. The effect of caffeine on subsequent sleep: a systematic review and meta-analysis. *Sleep Med Rev.* 2023;69:101764. doi:10.1016/j.smrv.2023.101764
