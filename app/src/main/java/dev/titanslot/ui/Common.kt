package dev.titanslot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * slot draws on a 720 x 480 panel. Everything here is laid out in those units ("u"), scaled
 * to fit a 720 x 800 box, which is the Titan 2 Elite's 1080 x 1200 at 1.5 px per unit.
 */
@Immutable
class Ui(val u: Float, private val density: Density, val widthPx: Float, val heightPx: Float) {
    fun dp(v: Float): Dp = with(density) { (v * u).toDp() }
    fun sp(v: Float): TextUnit = with(density) { (v * u).toSp() }
    fun px(v: Float): Float = v * u
    val widthU: Float get() = widthPx / u
    val heightU: Float get() = heightPx / u

    companion object {
        fun of(widthPx: Float, heightPx: Float, density: Density): Ui =
            Ui(minOf(widthPx / 720f, heightPx / 800f), density, widthPx, heightPx)
    }
}

val LocalUi = staticCompositionLocalOf<Ui> { error("Ui not provided") }

@Composable
fun label(size: Float, color: Color = Ink.menu, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f): TextStyle {
    val ui = LocalUi.current
    return TextStyle(
        fontFamily = LabelFont,
        fontSize = ui.sp(size),
        fontWeight = weight,
        color = color,
        letterSpacing = spacing.em,
    )
}

/** A row of "KEY action" hints along the bottom of a menu, like slot's legends. */
@Composable
fun Legend(vararg items: Pair<String, String>, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ui.dp(22f), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (key, action) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyCap(key)
                BasicText(
                    action,
                    modifier = Modifier.padding(start = ui.dp(7f)),
                    style = label(15f, Ink.dim),
                )
            }
        }
    }
}

@Composable
fun KeyCap(key: String) {
    val ui = LocalUi.current
    Box(
        modifier = Modifier
            .background(Ink.edge, RoundedCornerShape(ui.dp(5f)))
            .padding(horizontal = ui.dp(7f), vertical = ui.dp(1.5f)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(key, style = label(13f, Ink.menu, FontWeight.Bold, 0.04f))
    }
}

/** A full-width menu row with a lit bar when selected, label left and value right. */
@Composable
fun MenuRow(
    text: String,
    value: String?,
    selected: Boolean,
    pitch: Float,
    onClick: () -> Unit,
    valueLit: Boolean = selected,
    carets: Boolean = value != null,
    textSize: Float = 25f,
    valueWidth: Float = 150f,
) {
    val ui = LocalUi.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ui.dp(pitch))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (selected) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ui.dp(pitch - 8f))
                    .background(Ink.edge),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ui.dp(32f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text,
                modifier = Modifier.weight(1f),
                style = label(textSize, if (selected) Ink.menu else Ink.dim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value != null) {
                val ink = if (valueLit) Ink.menu else Ink.dim
                if (carets && selected) BasicText("‹", style = label(textSize, ink))
                BasicText(
                    value,
                    modifier = Modifier
                        .width(ui.dp(valueWidth))
                        .padding(horizontal = ui.dp(10f)),
                    style = label(textSize, ink).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (carets && selected) BasicText("›", style = label(textSize, ink))
            }
        }
    }
}
