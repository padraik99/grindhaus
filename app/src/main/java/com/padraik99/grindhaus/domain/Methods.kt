package com.padraik99.grindhaus.domain

/** What sits under the coffee: a basket for espresso, a filter for the rest. */
enum class FilterOption(val label: String) {
    SINGLE_WALL("Single-wall"),
    DOUBLE_WALL("Double-wall"),
    PAPER("Paper"),
    METAL("Metal"),
    NONE("Standard"),
}

/** How the last brew tasted. Drives the grind suggestion. */
enum class Taste(val label: String) { SOUR("Sour"), BALANCED("Balanced"), BITTER("Bitter") }

/**
 * Each brew method as data: which fields it uses and its starting defaults.
 * Defaults are starting points to edit, not measured values.
 *
 * [ratio] means grams of beverage out per gram in for espresso (yield is weighed),
 * and grams of water per gram of coffee for everything else.
 */
data class MethodSpec(
    val method: BrewMethod,
    val label: String,
    val filters: List<FilterOption>,
    val defaultDoseG: Double,
    val ratio: Double,
    /** Starting grind on the Maestri House 0-30 dial. */
    val startingGrind: Double,
    val weighsYield: Boolean,
    val timesShot: Boolean,
    val targetTimeS: IntRange? = null,
)

object Methods {

    val ALL: List<MethodSpec> = listOf(
        MethodSpec(
            BrewMethod.ESPRESSO, "Espresso",
            filters = listOf(FilterOption.SINGLE_WALL, FilterOption.DOUBLE_WALL),
            defaultDoseG = 18.0, ratio = 2.0, startingGrind = 3.0,
            weighsYield = true, timesShot = true, targetTimeS = 25..35,
        ),
        MethodSpec(
            BrewMethod.AEROPRESS, "AeroPress",
            filters = listOf(FilterOption.PAPER, FilterOption.METAL),
            defaultDoseG = 15.0, ratio = 15.0, startingGrind = 17.0,
            weighsYield = false, timesShot = false,
        ),
        MethodSpec(
            BrewMethod.FREDDO, "Caffè freddo",
            filters = listOf(FilterOption.PAPER, FilterOption.METAL),
            defaultDoseG = 15.0, ratio = 15.0, startingGrind = 17.0,
            weighsYield = false, timesShot = false,
        ),
        MethodSpec(
            BrewMethod.MOKA, "Moka",
            filters = listOf(FilterOption.NONE),
            defaultDoseG = 15.0, ratio = 10.0, startingGrind = 13.0,
            weighsYield = false, timesShot = false,
        ),
        MethodSpec(
            BrewMethod.POUR_OVER, "Pour-over",
            filters = listOf(FilterOption.PAPER),
            defaultDoseG = 15.0, ratio = 16.0, startingGrind = 27.0,
            weighsYield = false, timesShot = false,
        ),
        MethodSpec(
            BrewMethod.FRENCH_PRESS, "French press",
            filters = listOf(FilterOption.NONE),
            defaultDoseG = 30.0, ratio = 15.0, startingGrind = 30.0,
            weighsYield = false, timesShot = false,
        ),
    )

    /** Gear Patrick owns; the rest stay hidden until Settings lets him add them. */
    val OWNED: List<BrewMethod> = listOf(BrewMethod.ESPRESSO, BrewMethod.AEROPRESS, BrewMethod.MOKA, BrewMethod.FREDDO)

    fun spec(method: BrewMethod): MethodSpec = ALL.first { it.method == method }

    /** Pre-ground coffee goes in the forgiving double-wall basket. */
    fun defaultFilter(method: BrewMethod, preGround: Boolean): FilterOption {
        val filters = spec(method).filters
        return if (preGround && FilterOption.DOUBLE_WALL in filters) FilterOption.DOUBLE_WALL else filters.first()
    }
}
