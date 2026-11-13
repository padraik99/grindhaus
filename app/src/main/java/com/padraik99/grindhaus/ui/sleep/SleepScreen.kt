package com.padraik99.grindhaus.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padraik99.grindhaus.R
import com.padraik99.grindhaus.data.IntakeEvent
import com.padraik99.grindhaus.domain.CaffeineModel
import com.padraik99.grindhaus.domain.Cutoff
import com.padraik99.grindhaus.domain.Dose
import com.padraik99.grindhaus.domain.SleepForecast
import com.padraik99.grindhaus.domain.SleepForecaster
import com.padraik99.grindhaus.ui.brew.StepButton
import com.padraik99.grindhaus.ui.brew.clockTime
import com.padraik99.grindhaus.ui.components.BrandHeader
import com.padraik99.grindhaus.ui.components.NumberField
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.components.TimeOfDayField
import com.padraik99.grindhaus.ui.components.panel
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

@Composable
fun SleepRoute(viewModel: SleepViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SleepScreen(
        state = state,
        onBedtime = viewModel::setBedtime,
        onHalfLife = viewModel::setHalfLife,
        onLimit = viewModel::setLimit,
        onLogOther = viewModel::logOther,
        onRemove = viewModel::remove,
    )
}

@Composable
fun SleepScreen(
    state: SleepUiState,
    onBedtime: (java.time.LocalTime) -> Unit,
    onHalfLife: (Double) -> Unit,
    onLimit: (Double?) -> Unit,
    onLogOther: (Double) -> Unit,
    onRemove: (IntakeEvent) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandHeader("Sleep")
        val f = state.forecast ?: return@Column
        Tonight(f, state.doses, state.zone)
        LastCall(f, state.teaType.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() } + " tea", state.zone)
        Today(state.today, state.zone, onLogOther, onRemove)
        YourSleep(state, f, onBedtime, onHalfLife, onLimit)
    }
}

@Composable
private fun Tonight(f: SleepForecast, doses: List<Dose>, zone: ZoneId) {
    val bed = clockTime(f.bedtime.toInstant(), zone)
    val tone = if (f.overLimit) GrindhausColors.Bitter else GrindhausColors.Peak
    Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Tonight")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("In you now", f.nowMg, GrindhausColors.Foam, Modifier.weight(1f))
            Stat("After $bed", f.peakAfterBedMg, tone, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(tone, CircleShape))
            Text(
                if (f.overLimit) {
                    "${mg(f.peakAfterBedMg - f.limitMg)} over your ${mg(f.limitMg)} limit"
                } else {
                    "Under your ${mg(f.limitMg)} limit"
                },
                color = tone,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        CaffeineChart(f, doses, zone)
        Text(
            String.format(
                Locale.US,
                "Line: caffeine in you at your %s half-life. Shading: %s to %s, since nobody knows theirs exactly. " +
                    "Dashes: your %s limit. Dark band: the first 3 hours of sleep, where the check is made.",
                hours(f.halfLifeHours),
                hours(SleepForecaster.band(f.halfLifeHours).start),
                hours(SleepForecaster.band(f.halfLifeHours).endInclusive),
                mg(f.limitMg),
            ),
            color = GrindhausColors.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun Stat(label: String, valueMg: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = GrindhausColors.Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(String.format(Locale.US, "%.0f", valueMg), color = color, fontSize = 36.sp, fontWeight = FontWeight.Light)
            Text("mg", color = color.copy(alpha = 0.75f), fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun LastCall(f: SleepForecast, teaName: String, zone: ZoneId) {
    Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Last call")
        CutoffRow("A usual brew", f.usualBrewMg, f.usualBrewCutoff, f.now, zone)
        CutoffRow(teaName, f.teaMg, f.teaCutoff, f.now, zone)
        Text(
            "A usual brew is the middle of your last ten. These are the latest times each still keeps tonight under your limit.",
            color = GrindhausColors.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun CutoffRow(name: String, doseMg: Double, cutoff: Cutoff, now: Instant, zone: ZoneId) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = GrindhausColors.Foam, fontSize = 16.sp)
            Text("~${mg(doseMg)}", color = GrindhausColors.Muted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            when (cutoff) {
                is Cutoff.Until -> {
                    Text("by ${clockTime(cutoff.at, zone)}", color = GrindhausColors.Crema, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(inTime(now, cutoff.at), color = GrindhausColors.Muted, fontSize = 12.sp)
                }
                Cutoff.AnyTime -> Text("any time", color = GrindhausColors.Peak, fontSize = 15.sp)
                Cutoff.Passed -> Text("not tonight", color = GrindhausColors.Muted, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Today(events: List<IntakeEvent>, zone: ZoneId, onLogOther: (Double) -> Unit, onRemove: (IntakeEvent) -> Unit) {
    var other by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Today")
        if (events.isEmpty()) {
            Text("Nothing yet.", color = GrindhausColors.Muted, fontSize = 14.sp)
        }
        events.forEach { e ->
            Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clockTime(Instant.ofEpochMilli(e.takenAt), zone),
                    color = GrindhausColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.width(72.dp),
                )
                Text(e.label, color = GrindhausColors.FoamDim, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text(mg(e.mg), color = GrindhausColors.FoamDim, fontSize = 13.sp)
                // Brews are changed from their own editor; the rest can be taken back here.
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    if (e.brewLogId == null) {
                        IconButton(onClick = { onRemove(e) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = "Remove ${e.label}",
                                tint = GrindhausColors.Muted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(GrindhausColors.Line))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(other, { other = it }, "Caffeine from elsewhere", Modifier.weight(1f), suffix = "mg")
            OutlinedButton(
                onClick = {
                    other.toDoubleOrNull()?.takeIf { it > 0 }?.let(onLogOther)
                    other = ""
                },
                enabled = (other.toDoubleOrNull() ?: 0.0) > 0,
            ) { Text("Add", color = GrindhausColors.Crema) }
        }
    }
}

@Composable
private fun YourSleep(
    state: SleepUiState,
    f: SleepForecast,
    onBedtime: (java.time.LocalTime) -> Unit,
    onHalfLife: (Double) -> Unit,
    onLimit: (Double?) -> Unit,
) {
    val s = state.settings
    Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Your sleep")
        Text("Bedtime", color = GrindhausColors.FoamDim, fontSize = 14.sp)
        TimeOfDayField(s.bedtime, onBedtime)

        Text("Half-life", color = GrindhausColors.FoamDim, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepButton("−", "Shorter half-life") { onHalfLife(s.halfLifeHours - 0.5) }
            Text(hours(s.halfLifeHours), color = GrindhausColors.Foam, fontSize = 22.sp, fontWeight = FontWeight.Light)
            StepButton("+", "Longer half-life") { onHalfLife(s.halfLifeHours + 0.5) }
        }
        Text(
            "How long your body takes to clear half of it. About 5 h is typical for adults; it runs longer with some medications and shorter in smokers.",
            color = GrindhausColors.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

        NumberField(
            value = s.limitOverrideMg?.let { String.format(Locale.US, "%.0f", it) } ?: "",
            onValueChange = { onLimit(it.toDoubleOrNull()) },
            label = "Limit",
            modifier = Modifier.width(160.dp),
            decimals = false,
            placeholder = String.format(Locale.US, "%.0f", CaffeineModel.derivedThresholdMg(s.halfLifeHours)),
            suffix = "mg",
        )
        Text(
            if (f.limitIsDerived) {
                "Set from your half-life: what's left of a 107 mg coffee after 8.8 hours, the gap a 2023 review of sleep studies found was enough. Type a number to use your own."
            } else {
                "Your own limit. Clear it to go back to the one set from your half-life (${mg(CaffeineModel.derivedThresholdMg(s.halfLifeHours))})."
            },
            color = GrindhausColors.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

/** Caffeine across the day: the line at the set half-life, a band around it, the limit, now and the sleep window. */
@Composable
private fun CaffeineChart(f: SleepForecast, doses: List<Dose>, zone: ZoneId) {
    val measurer = rememberTextMeasurer()
    val band = SleepForecaster.band(f.halfLifeHours)
    val samples = remember(f, doses) {
        val n = 160
        val span = Duration.between(f.chartStart, f.chartEnd).toMillis()
        (0..n).map { i ->
            val t = f.chartStart.plusMillis(span * i / n)
            Triple(
                CaffeineModel.totalAt(doses, t, f.halfLifeHours),
                CaffeineModel.totalAt(doses, t, band.start),
                CaffeineModel.totalAt(doses, t, band.endInclusive),
            )
        }
    }
    val top = remember(samples, f.limitMg) {
        val highest = maxOf(samples.maxOf { it.third }, f.limitMg * 1.6, 40.0)
        ceil(highest / 25.0) * 25.0
    }
    val labelStyle = TextStyle(color = GrindhausColors.Muted, fontSize = 10.sp)
    val description = String.format(
        Locale.US,
        "Caffeine chart. Now %s. Highest after bedtime %s. Limit %s.",
        mg(f.nowMg), mg(f.peakAfterBedMg), mg(f.limitMg),
    )

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(GrindhausColors.Inset, RoundedCornerShape(14.dp))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val padTop = 14.dp.toPx()
        val padBottom = 22.dp.toPx()
        val padX = 10.dp.toPx()
        val w = size.width - 2 * padX
        val h = size.height - padTop - padBottom
        val span = Duration.between(f.chartStart, f.chartEnd).toMillis().toFloat()
        fun x(at: Instant) = padX + w * (Duration.between(f.chartStart, at).toMillis() / span)
        fun y(mg: Double) = padTop + h * (1f - (mg / top).toFloat())
        fun xi(i: Int) = padX + w * i / (samples.size - 1)

        // The sleep window, where the check is made.
        val bedX = x(f.bedtime.toInstant())
        drawRect(Color.Black.copy(alpha = 0.28f), Offset(bedX, 0f), Size(size.width - padX - bedX, size.height - padBottom))
        drawText(measurer, "sleep", Offset(bedX + 6.dp.toPx(), 2.dp.toPx()), labelStyle)

        // Band between the quicker and slower half-lives.
        val bandPath = Path().apply {
            samples.forEachIndexed { i, s -> if (i == 0) moveTo(xi(i), y(s.third)) else lineTo(xi(i), y(s.third)) }
            for (i in samples.indices.reversed()) lineTo(xi(i), y(samples[i].second))
            close()
        }
        drawPath(bandPath, GrindhausColors.Crema.copy(alpha = 0.16f))

        // The limit.
        val limitY = y(f.limitMg)
        drawLine(
            GrindhausColors.FoamDim.copy(alpha = 0.7f),
            Offset(padX, limitY),
            Offset(size.width - padX, limitY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )

        // The line: solid up to now, softer for what's still to come.
        val nowX = x(f.now)
        val line = Path().apply {
            samples.forEachIndexed { i, s -> if (i == 0) moveTo(xi(i), y(s.first)) else lineTo(xi(i), y(s.first)) }
        }
        drawPath(line, GrindhausColors.Crema.copy(alpha = 0.5f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val past = Path().apply {
            var started = false
            samples.forEachIndexed { i, s ->
                if (xi(i) <= nowX) {
                    if (!started) moveTo(xi(i), y(s.first)).also { started = true } else lineTo(xi(i), y(s.first))
                }
            }
            if (started) lineTo(nowX, y(f.nowMg))
        }
        drawPath(past, GrindhausColors.Crema, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

        // Now.
        drawLine(GrindhausColors.Foam.copy(alpha = 0.35f), Offset(nowX, padTop), Offset(nowX, size.height - padBottom), 1.dp.toPx())
        drawCircle(GrindhausColors.Foam, 4.dp.toPx(), Offset(nowX, y(f.nowMg)))
        val nowLabel = measurer.measure("now", labelStyle)
        drawText(nowLabel, topLeft = Offset((nowX - nowLabel.size.width / 2f).coerceIn(0f, size.width - nowLabel.size.width), 0f))

        // Each cup, along the bottom.
        doses.filter { it.takenAt >= f.chartStart && it.takenAt <= f.chartEnd }.forEach {
            drawCircle(GrindhausColors.Crema, 3.dp.toPx(), Offset(x(it.takenAt), size.height - padBottom))
        }

        // Hours, every three.
        var tick = f.chartStart.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        while (tick.hour % 3 != 0) tick = tick.plusHours(1)
        while (tick.toInstant() < f.chartEnd) {
            val label = measurer.measure(hourLabel(tick.hour), labelStyle)
            val tx = x(tick.toInstant()) - label.size.width / 2f
            if (tx > 0 && tx + label.size.width < size.width) {
                drawText(label, topLeft = Offset(tx, size.height - padBottom + 5.dp.toPx()))
            }
            tick = tick.plusHours(3)
        }
    }
}

private fun hourLabel(hour: Int): String {
    val h = (hour % 12).let { if (it == 0) 12 else it }
    return "$h ${if (hour < 12) "am" else "pm"}"
}

// Non-breaking spaces keep a number and its unit on the same line.
private fun mg(v: Double): String = String.format(Locale.US, "%.0f\u00A0mg", v)

private fun hours(v: Double): String =
    if (v % 1.0 == 0.0) String.format(Locale.US, "%.0f\u00A0h", v) else String.format(Locale.US, "%.1f\u00A0h", v)

/** "in 3 h 20 min", "in 45 min". */
private fun inTime(now: Instant, at: Instant): String {
    val minutes = Duration.between(now, at).toMinutes().coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "in $m min"
        m == 0L -> "in $h h"
        else -> "in $h h $m min"
    }
}
