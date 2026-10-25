package com.padraik99.grindhaus.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CaffeineEstimatesTest {

    private fun mg(dose: Double, method: BrewMethod, species: Species = Species.ARABICA, ratio: Double = 2.0) =
        CaffeineEstimates.coffee(dose, method, species, ratio).mg

    // Schmieder et al. 2023, Foods 12:2871: 20 g arabica gave ~130 / ~183 / ~204 mg.
    @Test
    fun `espresso matches Schmieder 2023 at 1-1, 1-2 and 1-3`() {
        assertEquals(130.0, mg(20.0, BrewMethod.ESPRESSO, ratio = 1.0), 1.0)
        assertEquals(182.0, mg(20.0, BrewMethod.ESPRESSO, ratio = 2.0), 1.0)
        assertEquals(204.0, mg(20.0, BrewMethod.ESPRESSO, ratio = 3.0), 1.0)
    }

    @Test
    fun `Patrick's 18 g double at Breville's 1-2 default is about 164 mg`() {
        assertEquals(163.8, mg(18.0, BrewMethod.ESPRESSO), 0.1)
    }

    @Test
    fun `espresso ratios between measured points are interpolated and held at the ends`() {
        assertEquals(18 * 7.8, mg(18.0, BrewMethod.ESPRESSO, ratio = 1.5), 1e-9)
        assertEquals(18 * 6.5, mg(18.0, BrewMethod.ESPRESSO, ratio = 0.8), 1e-9)
        assertEquals(18 * 10.2, mg(18.0, BrewMethod.ESPRESSO, ratio = 4.0), 1e-9)
    }

    // Angeloni et al. 2019, Food Research International: caffeine per cup from the doses used.
    @Test
    fun `moka, AeroPress, pour-over and French press match Angeloni 2019`() {
        assertEquals(171.0, mg(15.0, BrewMethod.MOKA), 1.0)
        assertEquals(165.0, mg(16.5, BrewMethod.AEROPRESS), 1.0)
        assertEquals(153.0, mg(15.0, BrewMethod.POUR_OVER), 1.0)
        assertEquals(103.5, mg(15.0, BrewMethod.FRENCH_PRESS), 1.0)
    }

    @Test
    fun `robusta doubles, decaf keeps 3 percent, a blend scales with its robusta share`() {
        val arabica = mg(18.0, BrewMethod.ESPRESSO)
        assertEquals(arabica * 2.0, mg(18.0, BrewMethod.ESPRESSO, Species.ROBUSTA), 1e-9)
        assertEquals(arabica * 0.03, mg(18.0, BrewMethod.ESPRESSO, Species.DECAF), 1e-9)
        val blend = CaffeineEstimates.coffee(18.0, BrewMethod.ESPRESSO, Species.BLEND, robustaShare = 0.3).mg
        assertEquals(arabica * 1.3, blend, 1e-9)
    }

    @Test
    fun `known species get a plus or minus 20 percent range`() {
        val e = CaffeineEstimates.coffee(18.0, BrewMethod.ESPRESSO, Species.ARABICA)
        assertEquals(e.mg * 0.8, e.lowMg, 1e-9)
        assertEquals(e.mg * 1.2, e.highMg, 1e-9)
    }

    @Test
    fun `not sure estimates as arabica but stretches the top to cover a half-robusta blend`() {
        val e = CaffeineEstimates.coffee(18.0, BrewMethod.ESPRESSO, Species.NOT_SURE)
        assertEquals(mg(18.0, BrewMethod.ESPRESSO), e.mg, 1e-9)
        assertEquals(e.mg * 1.6, e.highMg, 1e-9)
        val halfRobusta = CaffeineEstimates.coffee(18.0, BrewMethod.ESPRESSO, Species.BLEND, robustaShare = 0.5).mg
        assert(halfRobusta <= e.highMg)
    }

    // Tfouni et al. 2018: 10-15 mg per 100 mL for black and green infusions.
    @Test
    fun `tea defaults per 240 mL mug sit inside the measured infusion range`() {
        val black = CaffeineEstimates.tea(TeaType.BLACK).mg
        val green = CaffeineEstimates.tea(TeaType.GREEN).mg
        assertEquals(30.0, black, 1e-9)
        assertEquals(25.0, green, 1e-9)
        listOf(black, green).forEach { assert(it / 2.4 in 10.0..15.0) { "$it mg per mug is outside 10-15 mg/100 mL" } }
        assertEquals(0.0, CaffeineEstimates.tea(TeaType.HERBAL).mg, 0.0)
    }

    @Test
    fun `tea scales with volume`() {
        assertEquals(43.75, CaffeineEstimates.tea(TeaType.BLACK, volumeMl = 350.0).mg, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a blend without a robusta share is rejected`() {
        CaffeineEstimates.coffee(18.0, BrewMethod.ESPRESSO, Species.BLEND)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero dose is rejected`() {
        CaffeineEstimates.coffee(0.0, BrewMethod.ESPRESSO, Species.ARABICA)
    }
}
