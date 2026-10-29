package com.padraik99.grindhaus.data

import androidx.room.withTransaction
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.BuyAgain
import com.padraik99.grindhaus.domain.Comparison
import com.padraik99.grindhaus.domain.Ranking
import com.padraik99.grindhaus.domain.CaffeineEstimates
import com.padraik99.grindhaus.domain.GrindAdvisor
import com.padraik99.grindhaus.domain.Methods
import com.padraik99.grindhaus.domain.TeaType
import java.time.Clock
import java.time.Instant

/** Writes that touch several tables at once, each as one all-or-nothing transaction. */
class BrewRepository(private val db: GrindhausDatabase, private val clock: Clock) {

    private val beans = db.beanDao()
    private val brews = db.brewDao()
    private val grinders = db.grinderDao()
    private val teas = db.teaDao()

    private fun epochDay(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(clock.zone).toLocalDate().toEpochDay()

    private fun intakeLabel(method: BrewMethod, bean: Bean) = "${Methods.spec(method).label} · ${bean.title}"

    /** Makes sure there is a grinder to dial in against; seeds the Maestri House. */
    suspend fun defaultGrinder(): Grinder =
        grinders.active().firstOrNull() ?: Grinder.MAESTRI_HOUSE.let { it.copy(id = grinders.insert(it)) }

    /** Caffeine for this brew, from the bean's species and the method's measured yield. */
    fun caffeineMg(bean: Bean, method: BrewMethod, doseG: Double, outG: Double): Double {
        val ratio = if (Methods.spec(method).weighsYield && doseG > 0) outG / doseG else CaffeineEstimates.DEFAULT_ESPRESSO_RATIO
        return CaffeineEstimates.coffee(doseG, method, bean.species, ratio, bean.robustaShare).mg
    }

    /**
     * Logs a brew: the brew itself, its caffeine, the dial-in memory for next time,
     * and the bag's opening date if this is its first brew.
     *
     * With [autoApplyGrind], a sour or bitter result moves the remembered grind one step.
     */
    suspend fun logBrew(log: BrewLog, bean: Bean, grinder: Grinder, autoApplyGrind: Boolean): Long =
        db.withTransaction {
            val id = brews.insertLog(log)
            brews.insertIntake(
                IntakeEvent(
                    takenAt = log.brewedAt,
                    mg = log.caffeineMg,
                    source = IntakeSource.BREW,
                    brewLogId = id,
                    label = intakeLabel(log.method, bean),
                ),
            )
            val nextGrind = if (autoApplyGrind) {
                GrindAdvisor.suggest(log.taste, log.grindSetting, grinder.scale)?.grind ?: log.grindSetting
            } else {
                log.grindSetting
            }
            brews.savePreset(
                DialPreset(
                    beanId = log.beanId, method = log.method.dialKey, grinderId = log.grinderId, filter = log.filter,
                    grindSetting = nextGrind, doseG = log.doseG, outG = log.outG, updatedAt = clock.millis(),
                ),
            )
            beans.markOpened(log.beanId, epochDay(log.brewedAt))
            id
        }

    /**
     * Saves corrections to a brew, including its coffee, method and time. Caffeine is
     * recalculated for the (possibly different) bean and method, and the intake record moves
     * to the corrected time, so tonight's numbers follow.
     *
     * If it's now the most recent brew of its kind, the recipe for the next one follows too:
     * dose and yield always, grind only when the grind (or the coffee, method or basket) was
     * corrected, so a grind suggestion already taken isn't undone.
     */
    suspend fun updateBrew(log: BrewLog, bean: Bean) = db.withTransaction {
        val before = brews.log(log.id)
        val spec = Methods.spec(log.method)
        val filter = log.filter.takeIf { it in spec.filters } ?: Methods.defaultFilter(log.method, bean.preGround)
        val fixed = log.copy(
            beanId = bean.id,
            filter = filter,
            caffeineMg = caffeineMg(bean, log.method, log.doseG, log.outG),
        )
        brews.updateLog(fixed)
        brews.updateIntakeForLog(fixed.id, fixed.caffeineMg, fixed.brewedAt, intakeLabel(fixed.method, bean))
        beans.markOpened(bean.id, epochDay(fixed.brewedAt))

        val key = fixed.method.dialKey
        val latest = brews.lastLogIn(bean.id, key.dialFamily, fixed.grinderId, filter)
        if (latest?.id == fixed.id) {
            val preset = brews.preset(bean.id, key, fixed.grinderId, filter)
            val moved = before == null || before.beanId != fixed.beanId || before.method.dialKey != key ||
                before.filter != filter || before.grindSetting != fixed.grindSetting
            brews.savePreset(
                DialPreset(
                    beanId = bean.id, method = key, grinderId = fixed.grinderId, filter = filter,
                    grindSetting = if (moved || preset == null) fixed.grindSetting else preset.grindSetting,
                    doseG = fixed.doseG, outG = fixed.outG, updatedAt = clock.millis(),
                ),
            )
        }
    }

    /** Changes to the next shot's recipe, made on the Brew card. */
    suspend fun savePreset(preset: DialPreset) = brews.savePreset(preset.copy(updatedAt = clock.millis()))

    /** Takes back a brew logged by mistake, along with its caffeine. */
    suspend fun deleteBrew(log: BrewLog) = db.withTransaction {
        brews.deleteIntakeForLog(log.id)
        brews.deleteLog(log)
    }

    /**
     * Finishes a bag: archives it, records "buy again?", and if it was compared with the
     * current favourite, records the answer and nudges both rankings.
     */
    suspend fun finishBag(beanId: Long, buyAgain: BuyAgain?, comparedWith: Long?, outcome: Comparison?) =
        db.withTransaction {
            beans.setArchived(beanId, true)
            recordVerdict(beanId, buyAgain, comparedWith, outcome)
        }

    /** The same questions without archiving, for changing your mind later. */
    suspend fun updateVerdict(beanId: Long, buyAgain: BuyAgain?, comparedWith: Long?, outcome: Comparison?) =
        db.withTransaction { recordVerdict(beanId, buyAgain, comparedWith, outcome) }

    private suspend fun recordVerdict(beanId: Long, buyAgain: BuyAgain?, comparedWith: Long?, outcome: Comparison?) {
        beans.setBuyAgain(beanId, buyAgain)
        if (comparedWith == null || outcome == null || comparedWith == beanId) return
        val mine = beans.get(beanId) ?: return
        val theirs = beans.get(comparedWith) ?: return
        val (a, b) = Ranking.update(mine.rankScore, theirs.rankScore, outcome)
        beans.setRankScore(beanId, a)
        beans.setRankScore(comparedWith, b)
        beans.insertComparison(BeanComparison(beanId = beanId, otherBeanId = comparedWith, outcome = outcome, comparedAt = clock.millis()))
    }

    suspend fun undoTea(intakeId: Long) = brews.deleteStandaloneIntake(intakeId)

    /** Logs a cup of tea now; a saved tea gives it its name and counts towards "most used". */
    suspend fun logTea(type: TeaType, saved: SavedTea? = null): Long = db.withTransaction {
        saved?.let { teas.countUse(it.id) }
        brews.insertIntake(
            IntakeEvent(
                takenAt = clock.millis(),
                mg = CaffeineEstimates.tea(type).mg,
                source = IntakeSource.TEA,
                label = saved?.name ?: "${type.name.lowercase().replaceFirstChar { it.uppercase() }} tea",
            ),
        )
    }

    suspend fun saveTea(name: String, type: TeaType): Long = teas.insert(SavedTea(name = name.trim(), type = type))

    suspend fun updateTea(tea: SavedTea) = teas.update(tea.copy(name = tea.name.trim()))

    /** Forgets a saved tea. Cups already logged keep their name and caffeine. */
    suspend fun removeTea(tea: SavedTea) = teas.delete(tea)
}
