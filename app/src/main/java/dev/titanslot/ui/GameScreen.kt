package dev.titanslot.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import dev.titanslot.app.GameController
import dev.titanslot.app.SystemStatus
import dev.titanslot.core.Platform
import dev.titanslot.data.Scaling
import dev.titanslot.game.GameSession
import dev.titanslot.input.Button
import kotlin.math.floor
import kotlin.math.min

/**
 * The picture's size on the panel. Integer scaling uses the largest whole multiple that fits
 * (6x for GB and GBC, 4x for GBA on 1080 x 1200).
 */
fun gameViewSize(w: Float, h: Float, platform: Platform, scaling: Scaling): Pair<Int, Int> {
    val fit = min(w / platform.nativeW, h / platform.nativeH)
    val whole = floor(fit)
    val k = if (scaling == Scaling.INTEGER && whole >= 1f) whole else fit
    return (platform.nativeW * k).toInt() to (platform.nativeH * k).toInt()
}

@Composable
fun GameScreen(game: GameController) {
    val session = game.session
    val context = LocalContext.current
    val ui = LocalUi.current
    LaunchedEffect(session) { session.prepare(context) }
    LaunchedEffect(session.ready) { if (session.ready) game.onReady() }

    // The screen stays on while a game runs (cutscenes, reading), but may sleep on a paused one.
    val view = LocalView.current
    val awake = session.ready && !session.paused && session.error == null
    DisposableEffect(view, awake) {
        view.keepScreenOn = awake
        onDispose { view.keepScreenOn = false }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val (vw, vh) = gameViewSize(w, h, session.cart.platform, game.settings.scaling)
        if (session.prepared) {
            AndroidView(
                factory = { session.createView(it) },
                modifier = with(density) { Modifier.size(vw.toDp(), vh.toDp()) },
            )
        }

        val cover by animateFloatAsState(if (session.ready) 0f else 1f, tween(260), label = "cover")
        if (cover > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = cover)))

        StatusBar(game.status, session, gapTop = (h - vh) / 2f)

        session.error?.let { message ->
            Column(
                modifier = Modifier
                    .padding(horizontal = ui.dp(40f))
                    .background(Ink.housing, RoundedCornerShape(ui.dp(10f)))
                    .padding(ui.dp(26f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ui.dp(16f)),
            ) {
                BasicText("This cart won't start", style = label(26f, Ink.menu, FontWeight.Bold))
                BasicText(message, style = label(18f, Ink.dim).copy(textAlign = TextAlign.Center))
                Legend(game.keyMap.hint(Button.B) to "Eject")
            }
        }
    }
}

/**
 * Clock and battery while playing, plus the fast-forward / rewind marks. It sits in the
 * black band above the picture, or in a small tab over its corner when the picture fills the
 * screen, clear of the camera hole in the top left corner either way.
 */
@Composable
private fun StatusBar(status: SystemStatus, session: GameSession, gapTop: Float) {
    val ui = LocalUi.current
    val density = LocalDensity.current
    val speed = when {
        session.rewinding -> "◀◀"
        session.fastForwardLocked -> "▶▶ LOCK"
        session.fastForward -> "▶▶"
        else -> null
    }
    val items = @Composable {
        BasicText(status.clock, style = label(17f, Ink.dim, FontWeight.Bold, 0.06f))
        Battery(status)
        if (speed != null) {
            Box(
                Modifier
                    .background(Ink.edge, RoundedCornerShape(ui.dp(5f)))
                    .padding(horizontal = ui.dp(8f), vertical = ui.dp(1f)),
            ) { BasicText(speed, style = label(14f, Ink.menu, FontWeight.Bold, 0.08f)) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            gapTop >= ui.px(44f) -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { gapTop.coerceAtMost(ui.px(80f)).toDp() }),
                horizontalArrangement = Arrangement.spacedBy(ui.dp(18f), Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) { items() }
            else -> Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ui.dp(70f), end = ui.dp(14f))
                    .background(Ink.housing.copy(alpha = 0.8f), RoundedCornerShape(ui.dp(6f)))
                    .padding(horizontal = ui.dp(10f), vertical = ui.dp(3f)),
                horizontalArrangement = Arrangement.spacedBy(ui.dp(10f)),
                verticalAlignment = Alignment.CenterVertically,
            ) { items() }
        }
    }
}

@Composable
private fun Battery(status: SystemStatus) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
        Canvas(Modifier.size(ui.dp(if (status.charging) 33f else 24f), ui.dp(14f))) {
            val ink = Ink.dim
            var x = 0f
            val mid = size.height / 2f
            if (status.charging) {
                val bolt = Path().apply {
                    moveTo(ui.px(5f), mid - ui.px(7f))
                    lineTo(ui.px(1f), mid + ui.px(1f))
                    lineTo(ui.px(4.5f), mid + ui.px(1f))
                    lineTo(ui.px(3f), mid + ui.px(7f))
                    lineTo(ui.px(8f), mid - ui.px(1.5f))
                    lineTo(ui.px(4.5f), mid - ui.px(1.5f))
                    close()
                }
                drawPath(bolt, ink)
                x = ui.px(11f)
            }
            val gw = ui.px(20f)
            val gh = ui.px(11f)
            drawRoundRect(ink, Offset(x, mid - gh / 2f), Size(gw, gh), CornerRadius(ui.px(2f)), style = Stroke(ui.px(1.6f)))
            drawRect(ink, Offset(x + gw, mid - gh / 4f), Size(ui.px(2f), gh / 2f))
            if (status.battery >= 0) {
                val inner = (gw - ui.px(4.4f)) * status.battery / 100f
                drawRect(ink, Offset(x + ui.px(2.2f), mid - gh / 2f + ui.px(2.2f)), Size(inner, gh - ui.px(4.4f)))
            }
        }
        if (status.battery >= 0) BasicText("${status.battery}%", style = label(17f, Ink.dim, FontWeight.Bold, 0.06f))
    }
}
