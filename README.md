# Grindhaus

A personal Android app for a Pixel 8 Pro: it remembers how each bean was dialed in, logs every brew with a taste rating, and estimates how much caffeine will still be in the body at bedtime. Everything stays on the phone.

**Status:** build step 6.5 of 10. Brew Bar works: pick a bean and method, see the remembered grind, dose and caffeine, time the shot, log each brew with a rating and taste, and get a one-step grind suggestion after a sour or bitter result. Any brew can be corrected or deleted later, and a shot whose yield wasn't weighed is kept and marked as such. The Vault keeps beans, label photos, freshness and each bag's dial history, and sorts by favourites from a "buy again?" verdict and comparisons asked when a bag is finished. Sleep is still a placeholder; the engine behind it is built and tested. The full spec lives in the "Grindhaus Spec v2" doc.

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
- Database upgrades: a version 1 database (step 5) and a version 2 database (0.1.5, with brews and caffeine in it) both upgrade to version 3 with every bean and brew intact.
- Brew logging: caffeine recorded, dial-in memory kept per bean + method + grinder + basket, first brew opens the bag, mistaken brews can be deleted with their caffeine, and correcting a brew recalculates its caffeine.
- Grind advice: sour goes one step finer, bitter one step coarser, never past the end of the dial.
- Favourites: "buy again?" plus one-at-a-time comparisons ranked chess-style (Elo), so a single off-day answer barely moves an established favourite; Favorites sort order.
- Freshness: days since roast when the bag has a roast date, otherwise days since opening; past best-by is flagged.
- Vault screens: adding a bean, opening it, archiving it, all driven on a simulated phone.
- Brew Bar screens: defaults for a new bean, pre-ground handling, logging a sour shot and taking the suggestion, the evening bedtime warning, tea from the header, dial history on the bean page, an unweighed shot corrected later, deleting from the editor, the shot timer filling Time.
- Editing from the Vault: tapping a brew in the dial history opens it to edit or delete.
- Label photos: shrinking to 1600 px, turning sideways shots upright, cleanup of cancelled captures.
- Screens rendered on a simulated phone and saved as screenshots (the `screenshots` artifact on each run).

Not checked until it runs on the phone: the camera app itself, the date picker's feel, the timer's buzz at 25 and 35 s and keeping the screen on, the home-screen widget, and how it feels in the hand.

## Project layout

```
app/src/main/java/com/padraik99/grindhaus/
  domain/   caffeine engine and bedtime clock (pure Kotlin, no Android)
  data/     Room database (beans, grinders, dial presets, brew logs, caffeine intake), settings, label photos
  ui/       Compose screens: brew/, vault/, shared components/, theme/
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
