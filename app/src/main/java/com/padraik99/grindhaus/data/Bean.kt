package com.padraik99.grindhaus.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.padraik99.grindhaus.domain.Species

enum class RoastLevel { LIGHT, MEDIUM, MEDIUM_DARK, DARK }

@Entity(tableName = "beans")
data class Bean(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val roaster: String,
    val origin: String? = null,
    /** Defaults to "not sure"; a label saying 100% arabica settles it. */
    val species: Species = Species.NOT_SURE,
    /** Robusta fraction of a blend, 0..1. Only used when [species] is BLEND. */
    @ColumnInfo(name = "robusta_share") val robustaShare: Double? = null,
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
