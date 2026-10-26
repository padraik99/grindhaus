package com.padraik99.grindhaus.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.padraik99.grindhaus.domain.Species

enum class RoastLevel(val label: String, val step: Int) {
    LIGHT("Light", 1),
    MEDIUM("Medium", 2),
    MEDIUM_DARK("Medium-dark", 3),
    DARK("Dark", 4),
}

/**
 * One bag of coffee. Dates are stored as epoch days (days since 1970-01-01),
 * because a bag's dates have no time of day.
 */
@Entity(tableName = "beans")
data class Bean(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val roaster: String = "",
    val origin: String? = null,
    /** Defaults to "not sure"; a label saying 100% arabica settles it. */
    val species: Species = Species.NOT_SURE,
    /** Robusta fraction of a blend, 0..1. Only used when [species] is BLEND. */
    @ColumnInfo(name = "robusta_share") val robustaShare: Double? = null,
    @ColumnInfo(name = "roast_level") val roastLevel: RoastLevel,
    @ColumnInfo(name = "roast_date") val roastDate: Long? = null,
    /** Most supermarket bags print only this, which says little about freshness. */
    @ColumnInfo(name = "best_by_date") val bestByDate: Long? = null,
    @ColumnInfo(name = "purchase_date") val purchaseDate: Long? = null,
    /** Set automatically by the first brew from this bag; editable. */
    @ColumnInfo(name = "opened_date") val openedDate: Long? = null,
    /** Defaults to the label's net weight. */
    @ColumnInfo(name = "bag_grams") val bagGrams: Double? = null,
    @ColumnInfo(name = "pre_ground") val preGround: Boolean = false,
    /** File name inside the app's private labels folder. */
    @ColumnInfo(name = "label_photo_path") val labelPhotoPath: String? = null,
    /** Comma-separated. */
    @ColumnInfo(name = "tasting_tags") val tastingTags: String = "",
    val notes: String = "",
    val archived: Boolean = false,
) {
    val tags: List<String>
        get() = tastingTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
