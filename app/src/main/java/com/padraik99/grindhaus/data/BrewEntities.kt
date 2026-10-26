package com.padraik99.grindhaus.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.Comparison
import com.padraik99.grindhaus.domain.FilterOption
import com.padraik99.grindhaus.domain.GrindScale
import com.padraik99.grindhaus.domain.Taste

/** A grinder and its dial. A setting means nothing without knowing which grinder. */
@Entity(tableName = "grinders")
data class Grinder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "min_setting") val minSetting: Double,
    @ColumnInfo(name = "max_setting") val maxSetting: Double,
    val step: Double = 1.0,
    @ColumnInfo(name = "finer_is_lower") val finerIsLower: Boolean = true,
    /** "position:Label" pairs, comma-separated, as printed on the dial. */
    @ColumnInfo(name = "range_labels") val rangeLabels: String = "",
    val archived: Boolean = false,
) {
    val scale: GrindScale get() = GrindScale(minSetting, maxSetting, step, finerIsLower)

    val labels: List<Pair<Double, String>>
        get() = rangeLabels.split(',').mapNotNull { part ->
            val (pos, name) = part.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            pos.trim().toDoubleOrNull()?.let { it to name.trim() }
        }.sortedBy { it.first }

    /** The dial range a setting falls in: the nearest printed label at or above it. */
    fun rangeName(setting: Double): String? =
        labels.firstOrNull { setting <= it.first }?.second ?: labels.lastOrNull()?.second

    companion object {
        /** Patrick's grinder: espresso ~5 down to 0 (fine), then Mocca 15 ... Pourover 30. */
        val MAESTRI_HOUSE = Grinder(
            name = "Maestri House",
            minSetting = 0.0,
            maxSetting = 30.0,
            step = 1.0,
            finerIsLower = true,
            rangeLabels = "5:Espresso, 15:Mocca, 20:Siphon, 25:Filter, 30:Pourover",
        )
    }
}

/** The last settings used for one bean + method + grinder + filter. */
@Entity(
    tableName = "dial_presets",
    foreignKeys = [
        ForeignKey(entity = Bean::class, parentColumns = ["id"], childColumns = ["bean_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = Grinder::class, parentColumns = ["id"], childColumns = ["grinder_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["bean_id", "method", "grinder_id", "filter"], unique = true),
        Index("grinder_id"),
    ],
)
data class DialPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bean_id") val beanId: Long,
    val method: BrewMethod,
    @ColumnInfo(name = "grinder_id") val grinderId: Long,
    val filter: FilterOption,
    /** Null for pre-ground coffee. */
    @ColumnInfo(name = "grind_setting") val grindSetting: Double?,
    @ColumnInfo(name = "dose_g") val doseG: Double,
    /** Water for brewed methods, beverage out for espresso. */
    @ColumnInfo(name = "out_g") val outG: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** One brew as it actually happened, with how it tasted. */
@Entity(
    tableName = "brew_logs",
    foreignKeys = [
        ForeignKey(entity = Bean::class, parentColumns = ["id"], childColumns = ["bean_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = Grinder::class, parentColumns = ["id"], childColumns = ["grinder_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("bean_id"), Index("grinder_id"), Index("brewed_at")],
)
data class BrewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bean_id") val beanId: Long,
    val method: BrewMethod,
    @ColumnInfo(name = "grinder_id") val grinderId: Long,
    val filter: FilterOption,
    @ColumnInfo(name = "grind_setting") val grindSetting: Double?,
    @ColumnInfo(name = "dose_g") val doseG: Double,
    /** Water for brewed methods, weighed beverage for espresso. */
    @ColumnInfo(name = "out_g") val outG: Double,
    @ColumnInfo(name = "shot_time_s") val shotTimeS: Int? = null,
    /** 1-5. */
    val rating: Int? = null,
    val taste: Taste? = null,
    val notes: String = "",
    @ColumnInfo(name = "caffeine_mg") val caffeineMg: Double,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "brewed_at") val brewedAt: Long,
    /**
     * False when the cup wasn't weighed (or the water wasn't measured): [outG] then holds
     * the planned amount, and caffeine is estimated from it. The brew still counts for
     * grind and taste.
     */
    @ColumnInfo(name = "weighed", defaultValue = "1") val weighed: Boolean = true,
)

enum class IntakeSource { BREW, TEA, CUSTOM }

/** Every bit of caffeine taken, from any source. The sleep engine reads only this. */
@Entity(
    tableName = "intake_events",
    foreignKeys = [
        ForeignKey(entity = BrewLog::class, parentColumns = ["id"], childColumns = ["brew_log_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("brew_log_id"), Index("taken_at")],
)
data class IntakeEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    val mg: Double,
    val source: IntakeSource,
    @ColumnInfo(name = "brew_log_id") val brewLogId: Long? = null,
    val label: String,
)

/** One "better than your favourite?" answer, kept so the ranking can be rebuilt or audited. */
@Entity(
    tableName = "bean_comparisons",
    foreignKeys = [
        ForeignKey(entity = Bean::class, parentColumns = ["id"], childColumns = ["bean_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = Bean::class, parentColumns = ["id"], childColumns = ["other_bean_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("bean_id"), Index("other_bean_id")],
)
data class BeanComparison(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bean_id") val beanId: Long,
    @ColumnInfo(name = "other_bean_id") val otherBeanId: Long,
    val outcome: Comparison,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "compared_at") val comparedAt: Long,
)
