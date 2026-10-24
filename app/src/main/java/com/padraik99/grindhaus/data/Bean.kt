package com.padraik99.grindhaus.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Species { ARABICA, ROBUSTA, BLEND, DECAF }

enum class RoastLevel { LIGHT, MEDIUM, MEDIUM_DARK, DARK }

@Entity(tableName = "beans")
data class Bean(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val roaster: String,
    val origin: String? = null,
    val species: Species = Species.ARABICA,
    /** Caffeine by weight. Defaults from species; editable per bean. */
    @ColumnInfo(name = "caffeine_fraction") val caffeineFraction: Double = defaultCaffeineFraction(species),
    @ColumnInfo(name = "roast_level") val roastLevel: RoastLevel,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "roast_date") val roastDate: Long? = null,
    @ColumnInfo(name = "purchase_date") val purchaseDate: Long? = null,
    /** Defaults to the label's net weight. */
    @ColumnInfo(name = "bag_grams") val bagGrams: Double? = null,
    @ColumnInfo(name = "pre_ground") val preGround: Boolean = false,
    @ColumnInfo(name = "label_photo_path") val labelPhotoPath: String? = null,
    @ColumnInfo(name = "tasting_tags") val tastingTags: String = "",
    val notes: String = "",
    val archived: Boolean = false,
)

/** Approximate textbook values; the user can override per bean. */
fun defaultCaffeineFraction(species: Species): Double = when (species) {
    Species.ARABICA -> 0.012
    Species.ROBUSTA -> 0.022
    Species.BLEND -> 0.015
    Species.DECAF -> 0.0003
}
