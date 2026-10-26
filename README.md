# Grindhaus

A personal Android app for a Pixel 8 Pro: it remembers how each bean was dialed in, logs every brew with a taste rating, and estimates how much caffeine will still be in the body at bedtime. Everything stays on the phone.

**Status:** build step 5 of 10. The Vault works: add, edit, search and archive beans, with label photos and a freshness badge. Brew and Sleep tabs are placeholders; the caffeine engine behind them is built and tested. The full spec lives in the "Grindhaus Spec v2" doc.

## Getting the app onto the phone

Every push to GitHub builds the app and runs the tests (the **Actions** tab). Each green build of `main` is published as a release with a plain `.apk` attached.

- **Easiest:** point [Obtainium](https://github.com/ImranR98/Obtainium) at this repository once. It installs each new release as an update.
- **By hand:** open **Releases** (or `/releases/latest`), tap the `.apk`, and install. Android asks once to allow installs from that source.

Every build is signed with the same committed debug key, so new builds install over old ones and keep the app's data. A red build never becomes a release.

## What is checked automatically

- Caffeine engine: absorption peak, half-life decay, summed doses, the derived bedtime limit (32 mg at a 5 h half-life), and the after-bedtime check that catches a late cup.
- Caffeine estimates: each brew method's figure is pinned by a test to the study it came from; species scaling (robusta, decaf, blends, not sure); tea per mug.
- Sleep planner: latest time a drink still fits, e.g. after a 9:00 double, black tea until about 3:15 pm.
- Bedtime clock: after-midnight bedtimes and daylight-saving changes.
- Database: beans are archived, never deleted; search; the opening date is recorded once.
- Freshness: days since roast when the bag has a roast date, otherwise days since opening; past best-by is flagged.
- Vault screens: adding a bean, opening it, archiving it, all driven on a simulated phone.
- Label photos: shrinking to 1600 px, turning sideways shots upright, cleanup of cancelled captures.
- Screens rendered on a simulated phone and saved as screenshots (the `screenshots` artifact on each run).

Not checked until it runs on the phone: the camera app itself, the date picker's feel, vibration, the home-screen widget, and how it feels in the hand.

## Project layout

```
app/src/main/java/com/padraik99/grindhaus/
  domain/   caffeine engine and bedtime clock (pure Kotlin, no Android)
  data/     Room database (beans) and label photo storage
  ui/       Compose screens: vault/, shared components/, theme/
app/src/test/   unit, database and screenshot tests
app/schemas/    exported database schema, one file per version
```

## Building locally (optional)

Requires JDK 21 and the Android SDK. `./gradlew recordRoborazziDebug assembleDebug` (runs every test and saves screenshots to `app/screenshots/`).

## Sources for the caffeine numbers

- Gardiner C, et al. The effect of caffeine on subsequent sleep: a systematic review and meta-analysis. *Sleep Med Rev.* 2023;69:101764. doi:10.1016/j.smrv.2023.101764 (bedtime limit)
- Schmieder BKL, et al. Influence of flow rate, particle size, and temperature on espresso extraction kinetics. *Foods.* 2023;12:2871. doi:10.3390/foods12152871 (espresso by ratio)
- Angeloni G, et al. What kind of coffee do you drink? An investigation on effects of eight different extraction methods. *Food Res Int.* 2019 (moka, AeroPress, pour-over, French press)
- Tfouni SAV, et al. Caffeine in teas: levels, transference to infusion and estimated intake. *Food Sci Technol.* 2018;38(4):661-666. doi:10.1590/1678-457X.12217 (tea)
- Crozier TWM, et al. Espresso coffees, caffeine and chlorogenic acid intake: potential health implications. *Food Funct.* 2012 (real-world spread)
