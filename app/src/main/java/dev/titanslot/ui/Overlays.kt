package dev.titanslot.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.Typeface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import dev.titanslot.R
import dev.titanslot.app.AppState
import dev.titanslot.app.CartRow
import dev.titanslot.data.Cart
import kotlin.math.min
import dev.titanslot.app.GameController
import dev.titanslot.app.GameOverlay
import dev.titanslot.app.Toast
import dev.titanslot.app.Overlay
import dev.titanslot.app.PauseRow
import dev.titanslot.app.QuickRow
import dev.titanslot.data.SaveState
import dev.titanslot.input.Button
import dev.titanslot.input.KeyMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

@Composable
fun ShelfOverlays(app: AppState) {
    when (app.overlay) {
        Overlay.QUICK_MENU -> QuickMenu(app)
        Overlay.CART -> CartSheet(app)
        Overlay.CONTROLS -> Controls(app)
        Overlay.ABOUT -> About(app)
        null -> Unit
    }
}

@Composable
fun GameOverlays(game: GameController) {
    when (game.overlay) {
        GameOverlay.PAUSE -> PauseMenu(game)
        GameOverlay.SWITCHER -> Switcher(game)
        null -> Unit
    }
}

@Composable
private fun Sheet(alpha: Float = 1f, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.opening.copy(alpha = alpha))
            // Taps on the sheet's background must not fall through to the shelf.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) { content() }
}

@Composable
private fun Header(title: String, subtitle: String? = null) {
    val ui = LocalUi.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = ui.dp(48f), bottom = ui.dp(18f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(title.uppercase(), style = label(17f, Ink.dim, FontWeight.Bold, 0.2f))
        if (subtitle != null) {
            BasicText(
                subtitle,
                modifier = Modifier.padding(top = ui.dp(6f), start = ui.dp(32f), end = ui.dp(32f)),
                style = label(15f, Ink.faint).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- shelf -------------------------------------------------------------------------------

@Composable
private fun QuickMenu(app: AppState) {
    val ui = LocalUi.current
    val keys = app.keyMap
    Sheet {
        Column(Modifier.fillMaxSize()) {
            Header("Settings")
            QuickRow.entries.forEach { row ->
                MenuRow(
                    text = row.label,
                    value = app.quickValue(row),
                    selected = row == app.quickRow,
                    pitch = 52f,
                    onClick = { app.activateQuick(row) },
                )
            }
            Spacer(Modifier.weight(1f))
            Legend(
                "${keys.hint(Button.LEFT)} ${keys.hint(Button.RIGHT)}" to "Change",
                keys.hint(Button.A) to "Select",
                keys.hint(Button.B) to "Back",
                modifier = Modifier.padding(bottom = ui.dp(30f)),
            )
        }
    }
}

@Composable
private fun CartSheet(app: AppState) {
    val ui = LocalUi.current
    val cart = app.shelf?.current ?: return
    val keys = app.keyMap
    val rows = app.cartRows(cart)
    Sheet {
        Column(Modifier.fillMaxSize()) {
            Header("Cart", cart.platform.title)
            CartPreview(cart, Modifier.fillMaxWidth().height(ui.dp(PREVIEW_H)))
            Spacer(Modifier.height(ui.dp(10f)))
            rows.forEach { row ->
                MenuRow(
                    text = row.label,
                    value = app.cartValue(cart, row),
                    selected = row == app.cartRow,
                    pitch = 40f,
                    onClick = { app.activateCart(row) },
                    carets = row.cycles,
                    textSize = 20f,
                    valueWidth = if (row == CartRow.NAME) 400f else 210f,
                )
            }
            Spacer(Modifier.weight(1f))
            if (app.edit != null) {
                Legend(
                    "keyboard" to "Type",
                    "ENTER" to "Save",
                    "DEL" to "Erase",
                    "back" to "Cancel",
                    modifier = Modifier.padding(bottom = ui.dp(30f)),
                )
            } else {
                Legend(
                    "${keys.hint(Button.LEFT)} ${keys.hint(Button.RIGHT)}" to "Change",
                    keys.hint(Button.A) to "Select",
                    keys.hint(Button.B) to "Back",
                    modifier = Modifier.padding(bottom = ui.dp(30f)),
                )
            }
        }
    }
}

private const val PREVIEW_H = 170f

/** The cart as it will sit on the shelf, redrawn as you change it. */
@Composable
private fun CartPreview(cart: Cart, modifier: Modifier) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.open_sans) ?: Typeface.DEFAULT }
    val spec = CartArt.spec(cart.shape)
    val maxH = ui.px(PREVIEW_H)
    val w = min(cartWidth(cart) * ui.u * 0.8f, maxH / spec.aspect)
    val face by produceState<ImageBitmap?>(null, cart) {
        value = withContext(Dispatchers.Default) {
            runCatching { CartArt.render(cart, w.toInt(), typeface).asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        face?.let {
            Image(
                it,
                contentDescription = null,
                modifier = with(LocalDensity.current) { Modifier.size(w.toDp(), (w * spec.aspect).toDp()) },
            )
        }
    }
}

@Composable
private fun Controls(app: AppState) {
    val ui = LocalUi.current
    val keys = app.keyMap
    val list = rememberLazyListState()
    LaunchedEffect(app.controlsRow) {
        val first = list.firstVisibleItemIndex
        val visible = list.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        if (app.controlsRow < first || app.controlsRow >= first + visible - 1) {
            list.animateScrollToItem((app.controlsRow - visible / 2).coerceAtLeast(0))
        }
    }
    Sheet {
        Column(Modifier.fillMaxSize()) {
            Header("Controls", "Titan 2 Elite keyboard")
            LazyColumn(Modifier.weight(1f), state = list) {
                itemsIndexed(Button.entries) { i, button ->
                    val bound = keys.bindings[button].orEmpty()
                    val value = when {
                        app.listening == button -> "Press a key"
                        bound.isEmpty() -> "-"
                        else -> bound.joinToString(" / ") { KeyMap.keyName(it) }
                    }
                    MenuRow(
                        text = button.label,
                        value = value,
                        selected = i == app.controlsRow,
                        pitch = 44f,
                        onClick = { app.activateControl(i) },
                        carets = false,
                        textSize = 22f,
                        valueWidth = 220f,
                    )
                }
                item {
                    MenuRow(
                        text = "Reset to Titan Defaults",
                        value = null,
                        selected = app.controlsRow == Button.entries.size,
                        pitch = 44f,
                        onClick = { app.activateControl(Button.entries.size) },
                        textSize = 22f,
                    )
                }
            }
            Legend(
                keys.hint(Button.A) to if (app.listening != null) "Waiting for a key" else "Rebind",
                keys.hint(Button.B) to "Back",
                modifier = Modifier.padding(top = ui.dp(10f), bottom = ui.dp(30f)),
            )
        }
    }
}

@Composable
private fun About(app: AppState) {
    val ui = LocalUi.current
    Sheet {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = ui.dp(44f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header("About")
            Spacer(Modifier.weight(1f))
            BasicText("titan slot.", style = label(48f, Ink.menu, FontWeight.Bold))
            BasicText(
                "A cartridge-shelf frontend for the Unihertz Titan 2 Elite",
                modifier = Modifier.padding(top = ui.dp(8f), bottom = ui.dp(30f)),
                style = label(17f, Ink.dim).copy(textAlign = TextAlign.Center),
            )
            listOf(
                "Interface after slot by Brandon Kowalski, GPL-3.0",
                "Cart labels: slot's art set, from ScreenScraper (CC BY-NC-SA 4.0)",
                "Box art: the libretro thumbnails",
                "Cores: Gambatte, mGBA, melonDS, FCEUmm, Snes9x",
                "Frontend library: LibretroDroid by Filippo Scognamiglio",
                "Game process design after Lemuroid",
                "Type: Open Sans, SIL Open Font License",
                "Cart sounds: slot's recording of a GBA",
            ).forEach {
                BasicText(
                    it,
                    modifier = Modifier.padding(vertical = ui.dp(4f)),
                    style = label(16f, Ink.dim).copy(textAlign = TextAlign.Center),
                )
            }
            BasicText(
                "Games live in ${app.paths.root.path}",
                modifier = Modifier.padding(top = ui.dp(26f)),
                style = label(15f, Ink.faint).copy(textAlign = TextAlign.Center),
            )
            BasicText(
                "Game Boy, Nintendo DS, NES and Super Nintendo are trademarks of Nintendo. " +
                    "This app is not affiliated with Nintendo.",
                modifier = Modifier.padding(top = ui.dp(10f)),
                style = label(13f, Ink.faint).copy(textAlign = TextAlign.Center),
            )
            Spacer(Modifier.weight(1f))
            Legend(app.keyMap.hint(Button.B) to "Back", modifier = Modifier.padding(bottom = ui.dp(30f)))
        }
    }
}

// ---- game --------------------------------------------------------------------------------

@Composable
private fun PauseMenu(game: GameController) {
    val ui = LocalUi.current
    val session = game.session
    val keys = game.keyMap
    Sheet(alpha = 0.88f) {
        Column(Modifier.fillMaxSize()) {
            Header("Paused", session.cart.title)
            Spacer(Modifier.weight(1f))
            PauseRow.entries.forEach { row ->
                MenuRow(
                    text = row.label,
                    value = null,
                    selected = row == game.pauseRow,
                    pitch = 58f,
                    onClick = { game.activatePause(row) },
                )
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().padding(bottom = ui.dp(30f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ui.dp(12f)),
            ) {
                Legend(
                    "${keys.hint(Button.SELECT)}+${keys.hint(Button.R)}" to "Save",
                    "${keys.hint(Button.SELECT)}+${keys.hint(Button.L)}" to "Load",
                    "hold ${keys.hint(Button.MENU)}" to "Eject",
                )
                Legend(
                    "hold ${keys.hint(Button.FAST_FORWARD)}" to "Fast forward",
                    "hold ${keys.hint(Button.REWIND)}" to "Rewind",
                    keys.hint(Button.B) to "Resume",
                )
            }
        }
    }
}

@Composable
private fun Switcher(game: GameController) {
    val ui = LocalUi.current
    val states = game.switcher
    val keys = game.keyMap
    val slide by animateFloatAsState(game.switcherIndex.toFloat(), spring(stiffness = 420f), label = "polaroid")
    Sheet(alpha = 0.9f) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Header("Save States", "${game.switcherIndex + 1} of ${states.size}")
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                states.forEachIndexed { i, st ->
                    val off = i - slide
                    if (abs(off) > 2.5f) return@forEachIndexed
                    val lit = 1f - abs(off).coerceAtMost(1f)
                    Polaroid(
                        st,
                        modifier = Modifier
                            .offset(x = ui.dp(off * 330f))
                            .graphicsLayer {
                                val s = 0.78f + 0.22f * lit
                                scaleX = s
                                scaleY = s
                                rotationZ = off * 3f
                                alpha = 0.45f + 0.55f * lit
                            }
                            .clickable(remember { MutableInteractionSource() }, indication = null) {
                                game.selectSwitcher(i)
                            },
                    )
                }
            }
            Legend(
                "${keys.hint(Button.LEFT)} ${keys.hint(Button.RIGHT)}" to "Flick",
                keys.hint(Button.A) to "Load",
                keys.hint(Button.Y) to "Delete",
                keys.hint(Button.B) to "Back",
                modifier = Modifier.padding(bottom = ui.dp(30f)),
            )
        }
    }
}

@Composable
private fun Polaroid(state: SaveState, modifier: Modifier) {
    val ui = LocalUi.current
    val thumb by produceState<ImageBitmap?>(null, state.thumb) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(state.thumb.path)?.asImageBitmap() }.getOrNull()
        }
    }
    val caption = remember(state) {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.time))
        val day = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(state.time))
        if (state.auto) "Resume · $time" else "$day · $time"
    }
    Column(
        modifier
            .width(ui.dp(400f))
            .background(Ink.menu, RoundedCornerShape(ui.dp(3f)))
            .padding(start = ui.dp(16f), end = ui.dp(16f), top = ui.dp(16f), bottom = ui.dp(10f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val image = thumb
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(image?.let { it.width.toFloat() / it.height } ?: 1.2f)
                .background(Color(0xFF15151A)),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                BasicText("no picture", style = label(15f, Ink.faint))
            }
        }
        BasicText(
            caption,
            modifier = Modifier.padding(top = ui.dp(12f)),
            style = label(19f, Color(0xFF2A2A30)),
        )
    }
}

// ---- toast & setup --------------------------------------------------------------------------

@Composable
fun ToastHost(toast: Toast?, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        shown = true
        delay(1600)
        shown = false
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 120 else 300), label = "toast")
    if (a <= 0f || toast == null) return
    Box(modifier.fillMaxWidth().padding(top = ui.dp(20f)), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .alpha(a)
                .background(Ink.housing.copy(alpha = 0.92f), RoundedCornerShape(ui.dp(8f)))
                .padding(horizontal = ui.dp(18f), vertical = ui.dp(6f)),
        ) {
            BasicText(toast.text.uppercase(), style = label(17f, Ink.menu, FontWeight.Bold, 0.14f))
        }
    }
}

@Composable
fun SetupScreen(app: AppState) {
    val ui = LocalUi.current
    Column(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null) { app.onKey(Button.A, true) }
            .padding(horizontal = ui.dp(50f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText("titan slot.", style = label(56f, Ink.menu, FontWeight.Bold))
        BasicText(
            "Your games live in a TitanSlot folder on this phone, so you can copy them over USB. " +
                "Android asks you to allow All files access for that.",
            modifier = Modifier.padding(top = ui.dp(24f), bottom = ui.dp(40f)),
            style = label(19f, Ink.dim).copy(textAlign = TextAlign.Center),
        )
        Legend(app.keyMap.hint(Button.A) to "Allow access")
    }
}
