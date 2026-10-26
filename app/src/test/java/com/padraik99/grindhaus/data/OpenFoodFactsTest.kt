package com.padraik99.grindhaus.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padraik99.grindhaus.domain.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reading Open Food Facts answers. Runs on Robolectric for Android's built-in JSON parser. */
@RunWith(AndroidJUnit4::class)
class OpenFoodFactsTest {

    private fun found(json: String): BagDetails {
        val r = OpenFoodFacts.parse(json)
        assertTrue("expected Found, got $r", r is ProductResult.Found)
        return (r as ProductResult.Found).details
    }

    /** The real answer for a 1 kg bag of Lavazza Super Crema beans, fetched 26 Oct 2026. */
    @Test
    fun aRealWholeBeanBagFillsNameBrandAndWeight() {
        val d = found(
            """{"code":"8000070042025","product":{"brands":"Lavazza","categories_tags":["en:beverages-and-beverages-preparations",
            "en:plant-based-foods-and-beverages","en:plant-based-foods","en:beverage-preparations","en:coffees","en:coffee-beans"],
            "code":"8000070042025","product_name":"Lavazza Espresso Super Crema Coffee Beans","product_quantity":1000,
            "product_quantity_unit":"g","quantity":"1000 g"},"status":1,"status_verbose":"product found"}""",
        )
        assertEquals("Lavazza Espresso Super Crema Coffee Beans", d.title)
        assertEquals("Lavazza", d.roaster)
        assertEquals(1000.0, d.bagGrams!!, 0.0)
        assertEquals(false, d.preGround)
        // Nothing on that entry says arabica, robusta or a roast level, so those stay for you to set.
        assertEquals(null, d.species)
        assertEquals(null, d.roastLevel)
    }

    @Test
    fun decafGroundMediumRoastIsRecognised() {
        val d = found(
            """{"status":1,"product":{"product_name":"Decaf Medium Roast Ground Coffee","brands":"Some Roaster, Parent Co",
            "quantity":"12 oz","categories_tags":["en:coffees","en:ground-coffees","en:decaffeinated-coffees"],
            "origins":"en:colombia,en:costa-rica"}}""",
        )
        assertEquals(Species.DECAF, d.species)
        assertEquals(RoastLevel.MEDIUM, d.roastLevel)
        assertEquals(true, d.preGround)
        assertEquals(340.0, d.bagGrams!!, 0.0)
        assertEquals("Some Roaster", d.roaster)
        assertEquals("Colombia, Costa Rica", d.origin)
    }

    @Test
    fun decafWinsOverArabicaOnTheSameBag() {
        val d = found("""{"status":1,"product":{"product_name":"100% Arabica Decaf Dark Roast","product_quantity":"2","product_quantity_unit":"lb"}}""")
        assertEquals(Species.DECAF, d.species)
        assertEquals(RoastLevel.DARK, d.roastLevel)
        assertEquals(907.0, d.bagGrams!!, 0.0)
    }

    @Test
    fun arabicaAndRobustaTogetherMakeABlend() {
        val d = found("""{"status":1,"product":{"product_name":"Arabica & Robusta espresso blend whole bean"}}""")
        assertEquals(Species.BLEND, d.species)
        assertEquals(false, d.preGround)
    }

    @Test
    fun unknownBarcodesAndEmptyEntriesAreNotFound() {
        assertEquals(ProductResult.NotFound, OpenFoodFacts.parse("""{"code":"00000017","status":0,"status_verbose":"no code or invalid code"}"""))
        // A real reply seen while testing: "found", with nothing in it.
        assertEquals(ProductResult.NotFound, OpenFoodFacts.parse("""{"code":"4000000000006","product":{"product_name":""},"status":1}"""))
    }

    @Test
    fun aBusyPageInsteadOfJsonIsUnavailable() {
        // Open Food Facts answers with an HTML "temporarily unavailable" page when overloaded.
        assertEquals(ProductResult.Unavailable, OpenFoodFacts.parse("<!DOCTYPE html><html><title>Page temporarily unavailable</title></html>"))
    }

    @Test
    fun weightsThatCantBeABagAreIgnored() {
        val d = found("""{"status":1,"product":{"product_name":"Coffee pod","product_quantity":5.5,"product_quantity_unit":"g"}}""")
        assertEquals(null, d.bagGrams)
    }
}
