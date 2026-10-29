# Grindhaus

A personal Android app for a Pixel 8 Pro: it remembers how each bean was dialed in, logs every brew with a taste rating, and estimates how much caffeine will still be in the body at bedtime. Everything stays on the phone.

**Status:** build step 6.5 of 10, plus barcode lookup and the Home redesign. Home shows the bags on the shelf, a drawn sticker for each brew method (espresso, AeroPress, moka, caffè freddo), and recent brews with Again and Edit. A brew opens as its own page in the order you make it: coffee and method, set-up, the shot with its timer, taste, and when you drank it. Any brew can be corrected later, including its coffee, method and time, and its caffeine follows. A shot whose yield wasn't weighed is kept and marked as such. Teas you drink often can be saved and logged in one tap. New coffee can be filled in by scanning the bag's barcode: a bag you've had before fills in from your own Vault, otherwise from Open Food Facts. The Vault keeps beans, label photos, freshness and each bag's dial history, and sorts by favourites from a "buy again?" verdict and comparisons asked when a bag is finished. Sleep is still a placeholder; the engine behind it is built and tested. The full spec lives in the "Grindhaus Spec v2" doc.

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
- Database upgrades: version 1 (step 5), version 2 (0.1.5, with brews and caffeine), version 3 (0.1.6 and 0.1.7, with unweighed shots) and version 4 (0.1.8 and 0.1.9, with barcodes) all upgrade to version 5 with every bean and brew intact.
- Brew logging: caffeine recorded, dial-in memory kept per bean + method + grinder + basket, first brew opens the bag, mistaken brews can be deleted with their caffeine, and correcting a brew (even moving it to another coffee, method or time) recalculates its caffeine and moves it to the right time, and for the most recent brew carries into the next one's recipe. A caffè freddo shares the AeroPress dial-in and caffeine figure.
- Grind advice: sour goes one step finer, bitter one step coarser, never past the end of the dial.
- Favourites: "buy again?" plus one-at-a-time comparisons ranked chess-style (Elo), so a single off-day answer barely moves an established favourite; Favorites sort order.
- Freshness: days since roast when the bag has a roast date, otherwise days since opening; past best-by is flagged.
- Vault screens: adding a bean, opening it, archiving it, all driven on a simulated phone.
- Home and brew screens: the shelf, method stickers and version; defaults for a new bean; pre-ground handling; typing into a number replaces it; a sour shot saved and the next one offering one step finer; the evening bedtime warning; switching coffee mid-brew; an unweighed shot corrected later; a brew moved to the right coffee and day with its caffeine; deleting from the editor; the shot timer filling Time; a freddo starting from the AeroPress settings; tea with Undo; saved teas; setting the time a brew was drunk; larger phone text sizes; with the keyboard up, the Save button sits right on top of it and nothing slides under the status bar.
- Editing from the Vault: tapping a brew in the dial history opens it to edit or delete.
- Barcode lookup: a bag you've bought before is recognised from the Vault without going online; Open Food Facts answers (a real Lavazza entry, decaf, blends, ounces and pounds, empty entries, the "temporarily unavailable" page) turn into the right fields; an unknown barcode is still saved for next time; editing a bag offers no scan.
- Label photos: shrinking to 1600 px, turning sideways shots upright, cleanup of cancelled captures.
- Screens rendered on a simulated phone and saved as screenshots (the `screenshots` artifact on each run).

Not checked until it runs on the phone: how the stickers and handwriting look on the real screen, the camera app itself, Google's barcode scanner screen and the live call to Open Food Facts (checked by hand from the build machine, not in the tests), the date picker's feel, the timer's buzz at 25 and 35 s and keeping the screen on, the home-screen widget, and how it feels in the hand.

## Privacy

Everything stays on the phone. The one exception is **Scan barcode** on New coffee: it sends the bag's barcode number, and nothing else, to [Open Food Facts](https://world.openfoodfacts.org), an open product database. The scanner itself is Google Play services', so the app never asks for camera permission.

## Credits

- Labels under the method stickers are set in [Caveat](https://github.com/googlefonts/caveat) by Impallari Type, under the SIL Open Font License (`app/src/main/assets/licenses/Caveat-OFL.txt`). The app ships a semibold Latin cut of it.
- The method stickers were generated for this app by its owner.

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
