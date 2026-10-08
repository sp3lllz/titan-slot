package dev.titanslot.ui

import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.core.content.res.ResourcesCompat
import dev.titanslot.R
import dev.titanslot.app.AppState
import dev.titanslot.app.Mode
import dev.titanslot.app.ShelfModel
import dev.titanslot.data.Cart
import dev.titanslot.input.Button
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

private const val SIDE_SCALE = 0.78f
private const val SIDE_ALPHA = 0.55f
private const val PART = 130f
private const val SLOTS = 3
private const val SEATED_W = 240f
private const val LETTER_MS = 700L
/** Below the camera hole in the top left corner. */
private const val TABS_Y = 42f

private const val CATCH_IN = 0.42f
private const val CATCH_OUT = 0.62f
private const val CREEP = 0.03f

/** Cart widths in u, at the size they sit on the shelf. */
fun cartWidth(cart: Cart): Float = when (cart.platform) {
    dev.titanslot.core.Platform.GBA -> 360f
    dev.titanslot.core.Platform.GB, dev.titanslot.core.Platform.GBC -> 312f
}

/** The slot along the bottom of the panel, after slot's slot_chrome.rs. */
private class Chrome(ui: Ui) {
    val w = ui.widthPx
    val h = ui.heightPx
    val u = ui.u
    val cx = w / 2f
    val mouthH = 58f * u
    val bandTop = h - mouthH
    val lipH = 2f * u
    val mouthW = (SEATED_W + 14f) * u
    val mouthX = cx - mouthW / 2f
    val bayW = mouthW + 18f * u
    val bayX = cx - bayW / 2f
    val bayY = bandTop + lipH
    val slitY = bayY + 5f * u
    val slitH = 9f * u
    val recessH = 42f * u
    val scoopW = mouthW * 0.88f
    val scoopY = slitY + slitH
    val scoopD = recessH - (scoopY - bayY)
    val rimW = 2f * u
    val seatedY = bayY + 4f * u

    private fun depth(x: Float): Float = scoopD * (1f - (abs(x) / (scoopW / 2f)).pow(4f)).coerceAtLeast(0f)

    /** The dark scoop under the slit, as a closed shape from [top] down to its curved floor. */
    fun scoop(extra: Float = 0f): Path = Path().apply {
        val hw = scoopW / 2f
        moveTo(cx - hw, scoopY)
        var x = -hw
        while (x <= hw) {
            lineTo(cx + x, scoopY + depth(x) + extra)
            x += 2f
        }
        lineTo(cx + hw, scoopY)
        close()
    }

    val front: Path = Path().apply {
        val band = Path().apply {
            addRect(Rect(0f, bandTop, bayX, h))
            addRect(Rect(bayX + bayW, bandTop, w, h))
            addRect(Rect(bayX, scoopY, bayX + bayW, h))
        }
        op(band, scoop(), PathOperation.Difference)
    }

    val rim: Path = Path().apply {
        val hw = scoopW / 2f
        var x = -hw
        moveTo(cx - hw, scoopY + depth(-hw))
        while (x <= hw) {
            lineTo(cx + x, scoopY + depth(x) + rimW / 2f)
            x += 2f
        }
    }

    /** Where the hole's middle sits, for the platform name and jump letters. */
    val holeCentre: Float get() = slitY + (scoopY + scoopD - slitY) / 2f

    fun drawBack(d: DrawScope, alpha: Float) = with(d) {
        drawRect(Ink.housing, Offset(bayX, bandTop), Size(bayW, lipH), alpha)
        drawRect(Ink.edge, Offset(mouthX, bandTop), Size(mouthW, lipH), alpha)
        drawRect(Ink.recess, Offset(bayX, bayY), Size(bayW, recessH), alpha)
        drawRect(Ink.opening, Offset(mouthX, slitY), Size(mouthW, slitH), alpha)
        drawPath(scoop(), Ink.opening, alpha)
    }

    fun drawFront(d: DrawScope, alpha: Float) = with(d) {
        drawPath(front, Ink.housing, alpha)
        drawPath(rim, Ink.edge, alpha, style = Stroke(rimW))
    }
}

/** slot's insert travel: ease to the catch, creep through it, then push home. */
private fun travel(seat: Float, catch: Float): Float = when {
    seat < CATCH_IN -> catch * ease(seat / CATCH_IN)
    seat < CATCH_OUT -> catch + CREEP * (seat - CATCH_IN) / (CATCH_OUT - CATCH_IN)
    else -> {
        val caught = catch + CREEP
        caught + (1f - caught) * ease((seat - CATCH_OUT) / (1f - CATCH_OUT))
    }
}

private fun ease(u: Float): Float = u * u * u * (u * (u * 6f - 15f) + 10f)

/** Rendered cart faces for the carts near the centre of the shelf. */
class FaceCache(private val typeface: Typeface) {
    private val faces = mutableStateMapOf<String, ImageBitmap>()
    private val loading = mutableSetOf<String>()

    fun get(cart: Cart, widthPx: Int, scope: CoroutineScope): ImageBitmap? {
        // The cart's hash changes with its colour, shape, name or label, so edits redraw.
        val key = "${cart.key}@$widthPx#${cart.hashCode()}"
        faces[key]?.let { return it }
        if (loading.add(key)) {
            scope.launch(Dispatchers.Default) {
                val face = runCatching { CartArt.render(cart, widthPx, typeface) }.getOrNull()
                withContext(Dispatchers.Main) {
                    loading.remove(key)
                    if (face != null) faces[key] = face.asImageBitmap()
                }
            }
        }
        return null
    }

    /** Drops faces for carts that are no longer near the centre. */
    fun keep(carts: Set<String>) {
        if (faces.size <= 24) return
        faces.keys.filter { k -> carts.none { k.startsWith("$it@") } }.forEach { faces.remove(it) }
    }
}

@Composable
fun ShelfScreen(app: AppState) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val typeface = remember { ResourcesCompat.getFont(context, R.font.open_sans) ?: Typeface.DEFAULT }
    val faces = remember { FaceCache(typeface) }
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val chrome = remember(ui) { Chrome(ui) }

    val seat = remember { Animatable(0f) }
    val screen = remember { Animatable(0f) }
    // Spring the carousel, but only while a shelf is still moving.
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { app.shelves.any { !it.settled } }.first { it }
            var last = 0L
            while (app.shelves.any { !it.settled }) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(1f / 30f)
                    last = now
                    app.shelves.forEach { it.update(dt) }
                }
            }
        }
    }

    val letter = app.letter
    LaunchedEffect(letter) {
        if (letter != null) {
            kotlinx.coroutines.delay(LETTER_MS)
            app.clearLetter()
        }
    }

    val mode = app.mode
    LaunchedEffect(mode) {
        when (mode) {
            is Mode.Inserting -> {
                seat.snapTo(0f)
                screen.snapTo(0f)
                seat.animateTo(1f, tween(760, easing = LinearEasing))
                screen.animateTo(1f, tween(240))
                app.onInserted()
            }
            is Mode.Playing -> {
                seat.snapTo(1f)
                screen.snapTo(1f)
            }
            is Mode.Ejecting -> {
                screen.snapTo(1f)
                seat.snapTo(1f)
                screen.animateTo(0f, tween(200))
                seat.animateTo(0f, tween(640, easing = LinearEasing))
                app.onEjected()
            }
            else -> {
                seat.snapTo(0f)
                screen.snapTo(0f)
            }
        }
    }

    val shelf = app.shelf
    LaunchedEffect(shelf, shelf?.index) {
        val s = shelf ?: return@LaunchedEffect
        faces.keep((-SLOTS..SLOTS).mapNotNull { s.cartAt(it) }.map { s.carts[it].key }.toSet())
    }
    val moving = when (mode) {
        is Mode.Inserting -> mode.cart
        is Mode.Playing -> mode.cart
        is Mode.Ejecting -> mode.cart
        else -> null
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(app) {
                detectTapGestures(
                    onTap = { p ->
                        if (app.mode != Mode.Shelf || app.overlay != null) return@detectTapGestures
                        when {
                            p.x < size.width * 0.28f -> app.stepShelf(-1)
                            p.x > size.width * 0.72f -> app.stepShelf(1)
                            p.y < ui.px(TABS_Y + 50f) -> app.switchShelf(if (p.x < size.width / 2) -1 else 1)
                            else -> app.insert(fresh = false)
                        }
                    },
                    onLongPress = { p ->
                        if (app.mode == Mode.Shelf && app.overlay == null &&
                            p.x in size.width * 0.28f..size.width * 0.72f
                        ) app.insert(fresh = true)
                    },
                )
            }
            .pointerInput(app) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                ) { change, dx ->
                    if (app.mode != Mode.Shelf || app.overlay != null) return@detectHorizontalDragGestures
                    change.consume()
                    acc += dx
                    val step = ui.px(110f)
                    while (abs(acc) > step) {
                        app.stepShelf(if (acc > 0) -1 else 1)
                        acc -= step * sign(acc)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = seat.value
            val recede = if (moving != null) min(1f, s * 2.2f) else 0f

            drawTabs(app, ui, measurer, alpha = 1f - recede)

            if (shelf != null) {
                drawRow(shelf, ui, faces, scope, hidden = moving?.key, recede = recede)
                if (moving == null) drawCaption(shelf, ui, measurer)
            } else if (app.scanned) {
                drawEmpty(app, ui, measurer)
            }

            chrome.drawBack(this, 1f)
            if (moving != null) drawMoving(moving, s, ui, chrome, faces, scope)
            else drawSlotName(app, ui, chrome, measurer)
            chrome.drawFront(this, 1f)
            drawFooter(app, ui, chrome, measurer)
            if (moving == null && shelf != null) drawHints(app, shelf, ui, chrome, measurer)

            if (screen.value > 0f) drawRect(Color.Black, alpha = screen.value)
        }
    }
}

private fun DrawScope.drawRow(
    shelf: ShelfModel,
    ui: Ui,
    faces: FaceCache,
    scope: CoroutineScope,
    hidden: String?,
    recede: Float,
) {
    val u = ui.u
    val cx = size.width / 2f
    for (slot in -SLOTS..SLOTS) {
        val i = shelf.cartAt(slot) ?: continue
        val cart = shelf.carts[i]
        if (cart.key == hidden) continue
        val offset = shelf.ride + slot - shelf.scroll
        val t = min(abs(offset), 1f)
        val scale = 1f + (SIDE_SCALE - 1f) * t
        val alpha = (1f + (SIDE_ALPHA - 1f) * t) * (1f - recede)
        if (alpha <= 0f) continue
        val cw = cartWidth(cart) * u
        val ch = cw * CartArt.spec(cart.shape).aspect
        val w = cw * scale
        val h = ch * scale
        val away = sign(offset) * (1f + abs(offset))
        val x = cx + offset * cw - w / 2f + away * PART * u * recede
        if (x + w <= 0f || x >= size.width) continue
        val foot = restCentre(ui) + ch / 2f
        drawCart(cart, faces, scope, x, foot - h, w, h, cw, alpha)
    }
}

/** The vertical centre carts rest on, between the tabs and the caption. */
private fun restCentre(ui: Ui): Float = ui.px(TABS_Y + 40f) + (ui.heightPx - ui.px(58f + 92f) - ui.px(TABS_Y + 40f)) / 2f

private fun DrawScope.drawCart(
    cart: Cart,
    faces: FaceCache,
    scope: CoroutineScope,
    x: Float, y: Float, w: Float, h: Float,
    fullW: Float,
    alpha: Float,
) {
    val face = faces.get(cart, fullW.roundToInt(), scope)
    if (face == null) {
        val c = Color(cart.shell.rgb or 0xFF000000.toInt())
        drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(w * 0.03f), alpha = alpha)
        return
    }
    withTransform({
        translate(x, y)
        scale(w / face.width, h / face.height, pivot = Offset.Zero)
    }) {
        drawImage(face, alpha = alpha)
    }
}

private fun DrawScope.drawMoving(
    cart: Cart,
    seat: Float,
    ui: Ui,
    chrome: Chrome,
    faces: FaceCache,
    scope: CoroutineScope,
) {
    val aspect = CartArt.spec(cart.shape).aspect
    val w0 = cartWidth(cart) * ui.u
    val h0 = w0 * aspect
    val sw = SEATED_W * ui.u
    val sh = sw * aspect
    val foot0 = restCentre(ui) + h0 / 2f
    val seatedFoot = chrome.seatedY + sh
    val catch = (chrome.bandTop - foot0) / (seatedFoot - foot0)
    val tr = travel(seat.coerceIn(0f, 1f), catch)
    val k = min(tr / catch, 1f)
    val cw = w0 + (sw - w0) * k
    val ch = h0 + (sh - h0) * k
    val y = foot0 + (seatedFoot - foot0) * tr - ch
    drawCart(cart, faces, scope, chrome.cx - cw / 2f, y, cw, ch, w0, 1f)
}

private fun DrawScope.drawTabs(app: AppState, ui: Ui, measurer: TextMeasurer, alpha: Float) {
    if (alpha <= 0f || app.shelves.isEmpty()) return
    val tabs = app.shelves
    val style = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(17f), fontWeight = FontWeight.Bold, letterSpacing = 0.14.em)
    val laid = tabs.map { measurer.measure(it.platform.tab, style) }
    val gap = ui.px(26f)
    val keys = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(13f), fontWeight = FontWeight.Bold)
    val left = measurer.measure(app.keyMap.hint(Button.L), keys)
    val right = measurer.measure(app.keyMap.hint(Button.R), keys)
    val capW = { l: androidx.compose.ui.text.TextLayoutResult -> l.size.width + ui.px(14f) }
    val caps = if (tabs.size > 1) capW(left) + capW(right) + 2 * ui.px(30f) else 0f
    val total = laid.sumOf { it.size.width } + gap * (laid.size - 1) + caps
    var x = (size.width - total) / 2f
    val y = ui.px(TABS_Y)
    if (tabs.size > 1) {
        drawKeyCap(left, Offset(x, y + ui.px(2f)), ui, alpha)
        x += capW(left) + ui.px(30f)
    }
    laid.forEachIndexed { i, l ->
        val lit = i == app.shelfIndex
        drawText(l, color = if (lit) Ink.menu else Ink.faint, topLeft = Offset(x, y), alpha = alpha)
        if (lit) {
            drawRect(Ink.menu, Offset(x, y + l.size.height + ui.px(3f)), Size(l.size.width.toFloat(), ui.px(2f)), alpha)
        }
        x += l.size.width + gap
    }
    if (tabs.size > 1) drawKeyCap(right, Offset(x - gap + ui.px(30f), y + ui.px(2f)), ui, alpha)
}

private fun DrawScope.drawKeyCap(l: androidx.compose.ui.text.TextLayoutResult, at: Offset, ui: Ui, alpha: Float) {
    val padX = ui.px(7f)
    val padY = ui.px(1.5f)
    drawRoundRect(
        Ink.edge, at, Size(l.size.width + padX * 2, l.size.height + padY * 2),
        CornerRadius(ui.px(5f)), alpha = alpha,
    )
    drawText(l, color = Ink.menu, topLeft = at + Offset(padX, padY), alpha = alpha)
}

private fun DrawScope.drawCaption(shelf: ShelfModel, ui: Ui, measurer: TextMeasurer) {
    val cart = shelf.current ?: return
    val settle = 1f - min(abs(shelf.scroll - shelf.ride) * 3f, 1f)
    if (settle <= 0f) return
    val cw = cartWidth(cart) * ui.u
    val ch = cw * CartArt.spec(cart.shape).aspect
    val style = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(21f), color = Ink.dim)
    val l = measurer.measure(
        cart.title, style, overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = (size.width - ui.px(80f)).toInt()),
    )
    val y = restCentre(ui) + ch / 2f + ui.px(26f)
    drawText(l, topLeft = Offset((size.width - l.size.width) / 2f, y), alpha = settle)
}

private fun DrawScope.drawSlotName(app: AppState, ui: Ui, chrome: Chrome, measurer: TextMeasurer) {
    val letter = app.letter
    val text = letter?.first?.toString() ?: app.shelf?.platform?.title?.uppercase() ?: return
    val style = TextStyle(
        fontFamily = LabelFont,
        fontSize = ui.sp(if (letter != null) 22f else 10f),
        fontWeight = FontWeight.Bold,
        letterSpacing = if (letter != null) 0.em else 0.2.em,
        color = if (letter != null) Ink.menu else Ink.faint,
    )
    val l = measurer.measure(text, style)
    drawText(l, topLeft = Offset(chrome.cx - l.size.width / 2f, chrome.holeCentre - l.size.height / 2f))
}

private fun DrawScope.drawFooter(app: AppState, ui: Ui, chrome: Chrome, measurer: TextMeasurer) {
    val style = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(16f), fontWeight = FontWeight.Bold, color = Ink.menu, letterSpacing = 0.06.em)
    val mid = chrome.bandTop + chrome.mouthH / 2f
    val margin = ui.px(44f)

    // Battery gauge.
    val gw = ui.px(22f)
    val gh = ui.px(11f)
    var x = margin
    val battery = app.status.battery
    if (app.status.charging) {
        val bolt = Path().apply {
            moveTo(x + ui.px(5f), mid - ui.px(7f))
            lineTo(x + ui.px(1f), mid + ui.px(1f))
            lineTo(x + ui.px(4.5f), mid + ui.px(1f))
            lineTo(x + ui.px(3f), mid + ui.px(7f))
            lineTo(x + ui.px(8f), mid - ui.px(1.5f))
            lineTo(x + ui.px(4.5f), mid - ui.px(1.5f))
            close()
        }
        drawPath(bolt, Ink.menu)
        x += ui.px(13f)
    }
    drawRoundRect(Ink.menu, Offset(x, mid - gh / 2f), Size(gw, gh), CornerRadius(ui.px(2f)), style = Stroke(ui.px(1.6f)))
    drawRect(Ink.menu, Offset(x + gw, mid - gh / 4f), Size(ui.px(2f), gh / 2f))
    if (battery >= 0) {
        val inner = (gw - ui.px(4.4f)) * battery / 100f
        drawRect(Ink.menu, Offset(x + ui.px(2.2f), mid - gh / 2f + ui.px(2.2f)), Size(inner, gh - ui.px(4.4f)))
        val pct = measurer.measure("$battery%", style)
        drawText(pct, topLeft = Offset(x + gw + ui.px(10f), mid - pct.size.height / 2f))
    }

    val clock = measurer.measure(app.status.clock, style)
    drawText(clock, topLeft = Offset(size.width - margin - clock.size.width, mid - clock.size.height / 2f))
}

private fun DrawScope.drawHints(app: AppState, shelf: ShelfModel, ui: Ui, chrome: Chrome, measurer: TextMeasurer) {
    val keys = app.keyMap
    val parts = buildList {
        add(keys.hint(Button.A) to "Play")
        add("hold ${keys.hint(Button.A)}" to "New game")
        add(keys.hint(Button.START) to "Cart")
        add(keys.hint(Button.MENU) to "Settings")
    }
    val keyStyle = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(13f), fontWeight = FontWeight.Bold, color = Ink.dim)
    val textStyle = TextStyle(fontFamily = LabelFont, fontSize = ui.sp(13f), color = Ink.faint)
    val laid = parts.map { (k, t) -> measurer.measure(k, keyStyle) to measurer.measure(t, textStyle) }
    val inner = ui.px(6f)
    val gap = ui.px(22f)
    val total = laid.sumOf { (k, t) -> (k.size.width + inner + t.size.width).toDouble() }.toFloat() + gap * (laid.size - 1)
    var x = (size.width - total) / 2f
    val y = chrome.bandTop - ui.px(30f)
    laid.forEach { (k, t) ->
        drawText(k, topLeft = Offset(x, y))
        x += k.size.width + inner
        drawText(t, topLeft = Offset(x, y))
        x += t.size.width + gap
    }
}

private fun DrawScope.drawEmpty(app: AppState, ui: Ui, measurer: TextMeasurer) {
    val keys = app.keyMap
    val missing = !app.paths.libraryAvailable
    val title = measurer.measure(
        if (missing) "${app.storage.libraryName} isn't in" else "No carts on the shelf yet",
        TextStyle(fontFamily = LabelFont, fontSize = ui.sp(26f), fontWeight = FontWeight.Bold, color = Ink.menu),
    )
    val body = measurer.measure(
        if (missing) {
            "Your games are on it. Put it back in, or move the shelf in Settings (${keys.hint(Button.MENU)}) › Game Storage."
        } else {
            "Press ${keys.hint(Button.A)} or tap here to add Game Boy, Game Boy Color and Game Boy Advance games."
        },
        TextStyle(fontFamily = LabelFont, fontSize = ui.sp(17f), color = Ink.dim, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        constraints = Constraints(maxWidth = (size.width - ui.px(110f)).toInt()),
    )
    val top = size.height * 0.36f
    drawText(title, topLeft = Offset((size.width - title.size.width) / 2f, top))
    drawText(body, topLeft = Offset((size.width - body.size.width) / 2f, top + title.size.height + ui.px(18f)))
}
