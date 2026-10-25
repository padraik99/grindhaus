package com.padraik99.grindhaus.domain

/**
 * How much caffeine a drink puts in the body, before any of it is cleared.
 *
 * Coffee is estimated from lab measurements of caffeine in the cup per gram of
 * arabica used, for each brew method, then scaled for species. Every figure carries
 * its source so it can be checked or replaced.
 */

enum class BrewMethod { ESPRESSO, AEROPRESS, MOKA, POUR_OVER, FRENCH_PRESS }

/** Robusta has roughly twice arabica's caffeine; blends sit between. */
enum class Species { ARABICA, ROBUSTA, BLEND, DECAF, NOT_SURE }

enum class TeaType { BLACK, GREEN, HERBAL }

/** A central estimate and the range it plausibly falls in, all in milligrams. */
data class CaffeineEstimate(val mg: Double, val lowMg: Double, val highMg: Double) {
    init {
        require(lowMg <= mg && mg <= highMg) { "Range $lowMg..$highMg must contain $mg" }
    }
}

object CaffeineEstimates {

    /** Spread applied to every estimate: bean, roast and grind vary from cup to cup. */
    const val UNCERTAINTY = 0.20

    /**
     * "Not sure" estimates as arabica but stretches the top of the range to cover a
     * 50/50 arabica-robusta blend (1.5x) plus the usual spread.
     */
    const val NOT_SURE_HIGH_MULTIPLIER = 1.6

    /** Decaf keeps about 3% of the caffeine. */
    const val DECAF_MULTIPLIER = 0.03

    /**
     * Espresso: caffeine in the cup per gram of arabica dose, by brew ratio (grams out
     * per gram in). Schmieder et al. 2023, Foods 12:2871, 20 g arabica:
     * ~130 mg at 1:1, ~183 mg at 1:2, ~204 mg at 1:3. Angeloni et al. 2019 measured
     * 8.7 mg/g for a classic 1:2 espresso, agreeing with the 1:2 point.
     */
    private val ESPRESSO_YIELD_BY_RATIO = listOf(1.0 to 6.5, 2.0 to 9.1, 3.0 to 10.2)

    /**
     * Other methods: caffeine in the cup per gram of arabica dose, from Angeloni et al.
     * 2019, Food Research International, single-origin arabica.
     * Moka 15 g -> 172 mg; AeroPress 16.5 g -> 165 mg; V60 15 g -> 152 mg;
     * French press 15 g -> 103 mg (low confidence: the paper states two doses).
     */
    private val FIXED_YIELD = mapOf(
        BrewMethod.MOKA to 11.4,
        BrewMethod.AEROPRESS to 10.0,
        BrewMethod.POUR_OVER to 10.2,
        BrewMethod.FRENCH_PRESS to 6.9,
    )

    const val DEFAULT_ESPRESSO_RATIO = 2.0

    /** Milligrams of caffeine in the cup per gram of arabica dose. */
    fun yieldMgPerGram(method: BrewMethod, espressoRatio: Double = DEFAULT_ESPRESSO_RATIO): Double =
        if (method == BrewMethod.ESPRESSO) interpolate(ESPRESSO_YIELD_BY_RATIO, espressoRatio)
        else FIXED_YIELD.getValue(method)

    /**
     * Multiplier on the arabica estimate. [robustaShare] is the robusta fraction
     * of a blend, 0..1; it is only read for [Species.BLEND].
     */
    fun speciesMultiplier(species: Species, robustaShare: Double? = null): Double = when (species) {
        Species.ARABICA, Species.NOT_SURE -> 1.0
        Species.ROBUSTA -> 2.0
        Species.DECAF -> DECAF_MULTIPLIER
        Species.BLEND -> {
            val share = requireNotNull(robustaShare) { "A blend needs its robusta share" }
            require(share in 0.0..1.0) { "Robusta share $share must be between 0 and 1" }
            1.0 + share
        }
    }

    fun coffee(
        doseGrams: Double,
        method: BrewMethod,
        species: Species,
        espressoRatio: Double = DEFAULT_ESPRESSO_RATIO,
        robustaShare: Double? = null,
    ): CaffeineEstimate {
        require(doseGrams > 0.0) { "Dose must be positive" }
        val mg = doseGrams * yieldMgPerGram(method, espressoRatio) * speciesMultiplier(species, robustaShare)
        val high = if (species == Species.NOT_SURE) mg * NOT_SURE_HIGH_MULTIPLIER else mg * (1 + UNCERTAINTY)
        return CaffeineEstimate(mg = mg, lowMg = mg * (1 - UNCERTAINTY), highMg = high)
    }

    /** Standard mug the tea defaults refer to. */
    const val MUG_ML = 240.0

    /**
     * Tea per 240 mL mug. Tfouni et al. 2018 (Food Sci. Technol. 38:661) measured
     * 10-15 mg per 100 mL for black and green infusions brewed 3 minutes.
     * Herbal is caffeine-free unless the blend contains tea or mate; editable.
     */
    fun teaDefaultMg(type: TeaType): Double = when (type) {
        TeaType.BLACK -> 30.0
        TeaType.GREEN -> 25.0
        TeaType.HERBAL -> 0.0
    }

    fun tea(type: TeaType, volumeMl: Double = MUG_ML): CaffeineEstimate {
        require(volumeMl > 0.0) { "Volume must be positive" }
        val mg = teaDefaultMg(type) * volumeMl / MUG_ML
        return CaffeineEstimate(mg = mg, lowMg = mg * (1 - UNCERTAINTY), highMg = mg * (1 + UNCERTAINTY))
    }

    /** Linear between measured points; held at the end values outside them. */
    private fun interpolate(points: List<Pair<Double, Double>>, x: Double): Double {
        require(x > 0.0) { "Ratio must be positive" }
        if (x <= points.first().first) return points.first().second
        if (x >= points.last().first) return points.last().second
        val (x0, y0) = points.last { it.first <= x }
        val (x1, y1) = points.first { it.first > x }
        return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    }
}
