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
import java.time.LocalDate

/** Writes that touch several tables at once, each as one all-or-nothing transaction. */
class BrewRepository(private val db: GrindhausDatabase, private val clock: Clock) {

    private val beans = db.beanDao()
    private val brews = db.brewDao()
    private val grinders = db.grinderDao()

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
                    label = "${Methods.spec(log.method).label} · ${bean.title}",
                ),
            )
            val nextGrind = if (autoApplyGrind) {
                GrindAdvisor.suggest(log.taste, log.grindSetting, grinder.scale)?.grind ?: log.grindSetting
            } else {
                log.grindSetting
            }
            brews.savePreset(
                DialPreset(
                    beanId = log.beanId, method = log.method, grinderId = log.grinderId, filter = log.filter,
                    grindSetting = nextGrind, doseG = log.doseG, outG = log.outG, updatedAt = log.brewedAt,
                ),
            )
            beans.markOpened(log.beanId, LocalDate.now(clock).toEpochDay())
            id
        }

    /**
     * Saves corrections to a brew. Caffeine is recalculated from the corrected dose and
     * yield, and today's intake follows. If it's the most recent brew of its kind, the
     * recipe for the next shot follows too: its dose and yield, and its grind only when the
     * grind itself was corrected (so a grind suggestion already taken isn't undone).
     */
    suspend fun updateBrew(log: BrewLog, bean: Bean) = db.withTransaction {
        val before = brews.log(log.id)
        val corrected = log.copy(caffeineMg = caffeineMg(bean, log.method, log.doseG, log.outG))
        brews.updateLog(corrected)
        brews.updateIntakeForLog(corrected.id, corrected.caffeineMg, corrected.brewedAt)

        val latest = brews.lastLog(log.beanId, log.method, log.grinderId, log.filter)
        if (latest?.id == log.id) {
            val preset = brews.preset(log.beanId, log.method, log.grinderId, log.filter)
            val grindCorrected = before != null && before.grindSetting != log.grindSetting
            brews.savePreset(
                DialPreset(
                    beanId = log.beanId, method = log.method, grinderId = log.grinderId, filter = log.filter,
                    grindSetting = if (grindCorrected || preset == null) log.grindSetting else preset.grindSetting,
                    doseG = log.doseG, outG = log.outG, updatedAt = clock.millis(),
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

    suspend fun logTea(type: TeaType): Long = brews.insertIntake(
        IntakeEvent(
            takenAt = clock.millis(),
            mg = CaffeineEstimates.tea(type).mg,
            source = IntakeSource.TEA,
            label = "${type.name.lowercase().replaceFirstChar { it.uppercase() }} tea",
        ),
    )
}
