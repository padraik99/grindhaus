package com.padraik99.grindhaus.ui.brew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import java.util.Locale

/** A shot stopwatch. Lives above the Brew Bar so its reading carries into the log screen. */
@Stable
class ShotTimerState(initialMs: Long = 0L) {
    var elapsedMs by mutableLongStateOf(initialMs)
        internal set
    var running by mutableStateOf(false)
        private set

    /** Whole seconds, rounded; null until the timer has been used. */
    val seconds: Int? get() = if (elapsedMs > 0) ((elapsedMs + 500) / 1000).toInt() else null

    fun start() {
        running = true
    }

    fun stop() {
        running = false
    }

    fun reset() {
        running = false
        elapsedMs = 0
    }

    companion object {
        val Saver: Saver<ShotTimerState, Long> = Saver(save = { it.elapsedMs }, restore = { ShotTimerState(it) })
    }
}

/**
 * Start when the pump starts, stop when it stops. A buzz marks the start and end of the
 * target window, and the screen stays on while it runs. The reading fills Time in the log.
 */
@Composable
fun ShotTimer(state: ShotTimerState, target: IntRange?) {
    val haptics = LocalHapticFeedback.current
    if (state.running) {
        LaunchedEffect(Unit) {
            val base = state.elapsedMs
            val start = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    val before = state.elapsedMs / 1000
                    state.elapsedMs = base + (now - start)
                    val after = state.elapsedMs / 1000
                    if (target != null && before != after && (after.toInt() == target.first || after.toInt() == target.last)) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
        }
        val view = LocalView.current
        DisposableEffect(view) {
            val was = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = was }
        }
    }

    val secs = state.elapsedMs / 1000
    val tenths = (state.elapsedMs % 1000) / 100
    val inWindow = target != null && secs.toInt() in target
    Row(
        Modifier
            .fillMaxWidth()
            .background(GrindhausColors.SurfaceHigh, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            String.format(Locale.US, "%d.%d s", secs, tenths),
            color = when {
                state.elapsedMs == 0L -> GrindhausColors.Muted
                inWindow -> GrindhausColors.Balanced
                else -> GrindhausColors.Foam
            },
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.semantics { contentDescription = "Shot timer $secs seconds" },
        )
        Spacer(Modifier.weight(1f))
        when {
            state.running -> OutlinedButton(onClick = state::stop) { Text("Stop", color = GrindhausColors.Crema) }
            state.elapsedMs > 0 -> {
                OutlinedButton(onClick = state::reset) { Text("Reset", color = GrindhausColors.FoamDim) }
                OutlinedButton(onClick = state::start) { Text("Resume", color = GrindhausColors.FoamDim) }
            }
            else -> OutlinedButton(onClick = state::start) { Text("Start timer", color = GrindhausColors.Crema) }
        }
    }
}
