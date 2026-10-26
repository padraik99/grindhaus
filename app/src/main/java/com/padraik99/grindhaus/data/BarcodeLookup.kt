package com.padraik99.grindhaus.data

import com.padraik99.grindhaus.domain.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** What a product database could say about a bag. Null means it didn't say. */
data class BagDetails(
    val title: String? = null,
    val roaster: String? = null,
    val origin: String? = null,
    val species: Species? = null,
    val roastLevel: RoastLevel? = null,
    val bagGrams: Double? = null,
    val preGround: Boolean? = null,
)

sealed interface ProductResult {
    data class Found(val details: BagDetails) : ProductResult
    data object NotFound : ProductResult
    /** Offline, timed out, or the service is down. */
    data object Unavailable : ProductResult
}

fun interface ProductLookup {
    suspend fun lookup(barcode: String): ProductResult
}

/** What scanning a bag turned up, best source first. */
sealed interface LookupOutcome {
    val barcode: String

    /** A bag you've had before: its details are yours, so they beat any database. */
    data class FromVault(override val barcode: String, val bean: Bean) : LookupOutcome
    data class Found(override val barcode: String, val details: BagDetails) : LookupOutcome
    data class NotFound(override val barcode: String) : LookupOutcome
    data class Unavailable(override val barcode: String) : LookupOutcome
}

class BarcodeLookup(private val beans: BeanDao, private val products: ProductLookup) {
    suspend fun lookup(barcode: String): LookupOutcome {
        beans.latestWithBarcode(barcode)?.let { return LookupOutcome.FromVault(barcode, it) }
        return when (val r = products.lookup(barcode)) {
            is ProductResult.Found -> LookupOutcome.Found(barcode, r.details)
            ProductResult.NotFound -> LookupOutcome.NotFound(barcode)
            ProductResult.Unavailable -> LookupOutcome.Unavailable(barcode)
        }
    }
}

/**
 * Open Food Facts, the open product database. Reads need no account, only an app name
 * in the User-Agent; the limit is 15 product reads a minute, far above one person scanning.
 */
class OpenFoodFacts(private val userAgent: String) : ProductLookup {

    override suspend fun lookup(barcode: String): ProductResult = withContext(Dispatchers.IO) {
        if (!barcode.all(Char::isDigit)) return@withContext ProductResult.NotFound
        val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=$FIELDS")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            when (conn.responseCode) {
                200 -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                404 -> ProductResult.NotFound
                else -> ProductResult.Unavailable
            }
        } catch (_: IOException) {
            ProductResult.Unavailable
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val FIELDS =
            "code,product_name,product_name_en,brands,quantity,product_quantity,product_quantity_unit," +
                "origins,categories_tags,labels_tags"

        /** Turns a product response into bag details. A page that isn't JSON counts as unavailable. */
        fun parse(json: String): ProductResult {
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return ProductResult.Unavailable
            if (root.optInt("status", 0) != 1) return ProductResult.NotFound
            val product = root.optJSONObject("product") ?: return ProductResult.NotFound
            val details = details(product)
            // An entry with no name, brand or weight tells us nothing worth filling in.
            return if (details.title == null && details.roaster == null && details.bagGrams == null) {
                ProductResult.NotFound
            } else {
                ProductResult.Found(details)
            }
        }

        internal fun details(p: JSONObject): BagDetails {
            val name = p.text("product_name") ?: p.text("product_name_en")
            val categories = p.tags("categories_tags")
            val labels = p.tags("labels_tags")
            val lowerName = name?.lowercase(Locale.US).orEmpty()
            return BagDetails(
                title = name,
                roaster = p.text("brands")?.split(',')?.map { it.trim() }?.firstOrNull { it.isNotEmpty() },
                origin = p.text("origins")?.let(::cleanOrigins),
                species = species(lowerName, categories, labels),
                roastLevel = roast(lowerName),
                bagGrams = grams(p),
                preGround = preGround(lowerName, categories),
            )
        }

        private fun species(name: String, categories: Set<String>, labels: Set<String>): Species? {
            val decaf = "en:decaffeinated-coffees" in categories || "en:decaffeinated" in labels ||
                "decaf" in name
            if (decaf) return Species.DECAF
            val arabica = "arabica" in name || "en:100-arabica" in labels || "en:arabica-coffees" in categories
            val robusta = "robusta" in name || "en:robusta-coffees" in categories
            return when {
                arabica && robusta -> Species.BLEND
                robusta -> Species.ROBUSTA
                arabica -> Species.ARABICA
                else -> null
            }
        }

        /** Only explicit roast words on the label; "espresso" alone says nothing about the roast. */
        private fun roast(name: String): RoastLevel? = when {
            "medium dark" in name || "medium-dark" in name -> RoastLevel.MEDIUM_DARK
            listOf("dark roast", "french roast", "italian roast", "espresso roast").any { it in name } -> RoastLevel.DARK
            "medium roast" in name -> RoastLevel.MEDIUM
            "light roast" in name || "blonde" in name -> RoastLevel.LIGHT
            else -> null
        }

        private fun preGround(name: String, categories: Set<String>): Boolean? = when {
            "en:ground-coffees" in categories -> true
            "en:coffee-beans" in categories || "en:whole-bean-coffees" in categories -> false
            "whole bean" in name -> false
            "ground" in name -> true
            else -> null
        }

        private val QUANTITY = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|g|oz|lbs?)\b""", RegexOption.IGNORE_CASE)

        private fun grams(p: JSONObject): Double? {
            val amount = p.opt("product_quantity")?.toString()?.toDoubleOrNull()
            val unit = p.text("product_quantity_unit")
            val fromFields = amount?.let { inGrams(it, unit ?: "g") }
            val fromText = p.text("quantity")?.let { q ->
                QUANTITY.find(q)?.let { m -> m.groupValues[1].replace(',', '.').toDoubleOrNull()?.let { inGrams(it, m.groupValues[2]) } }
            }
            return (fromFields ?: fromText)?.takeIf { it in 50.0..5_000.0 }?.roundToInt()?.toDouble()
        }

        private fun inGrams(amount: Double, unit: String): Double? = when (unit.lowercase(Locale.US)) {
            "g" -> amount
            "kg" -> amount * 1000
            "oz" -> amount * 28.3495
            "lb", "lbs" -> amount * 453.592
            else -> null
        }

        /** "en:colombia, Brazil" → "Colombia, Brazil". */
        private fun cleanOrigins(raw: String): String? = raw.split(',')
            .map { it.trim().substringAfter(':').replace('-', ' ') }
            .filter { it.isNotEmpty() }
            .joinToString(", ") { part -> part.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.US) } } }
            .ifEmpty { null }

        private fun JSONObject.text(key: String): String? =
            optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }

        private fun JSONObject.tags(key: String): Set<String> {
            val arr: JSONArray = optJSONArray(key) ?: return emptySet()
            return (0 until arr.length()).mapTo(mutableSetOf()) { arr.optString(it) }
        }
    }
}
