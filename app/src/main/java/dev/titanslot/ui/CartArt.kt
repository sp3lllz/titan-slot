package dev.titanslot.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import dev.titanslot.data.ArtFit
import dev.titanslot.data.Cart
import dev.titanslot.data.CartShape
import dev.titanslot.data.Finish
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Paints a cart's face into a bitmap: plastic, moulding, label recess and label.
 *
 * GB, GBC and GBA outlines and moulding are slot's (the SVGs in crates/slot-ui/assets, GPL-3.0),
 * in the same 240-wide view boxes. NES, SNES and DS follow the same recipe.
 */
object CartArt {
    private const val FILL_BIAS = 0.3f

    class Spec(
        val vw: Float,
        val vh: Float,
        outline: String,
        /** Label panel in view box units. */
        val label: RectF,
        /** Raised moulding: lit from the top left. */
        ridges: List<String> = emptyList(),
        /** Engraved moulding: lit from the bottom right. */
        cuts: List<String> = emptyList(),
        val mark: String? = null,
        val markX: Float = vw / 2f,
        val markY: Float = 0f,
        val markSize: Float = 0f,
        val topGloss: Boolean = false,
    ) {
        // Renders run on several threads at once, so each takes its own copies.
        private val outlinePath: Path = parse(outline)
        private val ridgePaths: List<Path> = ridges.map(::parse)
        private val cutPaths: List<Path> = cuts.map(::parse)
        val outline: Path get() = Path(outlinePath)
        val ridges: List<Path> get() = ridgePaths.map(::Path)
        val cuts: List<Path> get() = cutPaths.map(::Path)
        val aspect: Float get() = vh / vw
    }

    private fun parse(d: String): Path = PathParser.createPathFromPathData(d).apply {
        fillType = Path.FillType.EVEN_ODD
    }

    private fun rect(x: Float, y: Float, w: Float, h: Float) = "M$x ${y}h${w}v${h}h${-w}Z"

    private val GBA = Spec(
        vw = 240f, vh = 141f,
        outline = "M10.72 0C10.62 0 10.52 0.02 10.42 0.05L7.52 0.82C7.42 0.85 7.33 0.89 7.24 0.95L5.14 2.16C5.05 2.21 4.98 2.27 4.91 2.34L3.13 4.11C3.06 4.18 3 4.26 2.94 4.34L1.21 7.37C1.16 7.45 1.12 7.54 1.09 7.64L0.04 11.56C0.01 11.65 0 11.76 0 11.85L0 25.57C0 25.77 0.05 25.96 0.15 26.14L1.13 27.83C1.28 28.09 1.53 28.28 1.82 28.36L3.63 28.84C3.73 28.87 3.83 28.89 3.93 28.89L6.49 28.89L6.49 136.73C6.5 136.93 6.55 137.13 6.65 137.3L7.65 139.04C7.75 139.21 7.89 139.35 8.07 139.45L10.49 140.85C10.66 140.95 10.86 141 11.06 141L228.94 141C229.14 141 229.34 140.95 229.51 140.85L231.93 139.45C232.1 139.35 232.24 139.21 232.34 139.04L233.35 137.3C233.45 137.13 233.5 136.93 233.5 136.73L233.5 28.89L236.06 28.89C236.16 28.89 236.26 28.87 236.36 28.84L238.17 28.36C238.46 28.28 238.71 28.09 238.86 27.83L239.84 26.14C239.94 25.97 240 25.77 240 25.57L240 11.85C240 11.75 239.99 11.65 239.96 11.56L238.91 7.64C238.88 7.54 238.85 7.45 238.8 7.37L237.05 4.34C237 4.26 236.94 4.18 236.87 4.11L235.1 2.34C235.03 2.27 234.95 2.21 234.86 2.16L232.75 0.95C232.67 0.9 232.58 0.85 232.48 0.82L229.58 0.05C229.48 0.02 229.39 0 229.29 0Z",
        label = RectF(32.4f, 32.6f, 210f, 124.4f),
        ridges = listOf(
            "M115.75 7.38C107.02 7.43 82.33 9.89 71.84 12.22C58.15 15.27 46.73 19.66 33.02 22.44C33.02 22.44 69.23 15.81 73.49 15.09C98.02 10.96 119.3 11.51 119.3 11.51L120.59 11.51C120.59 11.51 141.88 10.96 166.4 15.09C170.67 15.81 206.87 22.44 206.87 22.44C193.17 19.66 181.74 15.27 168.06 12.22C157.22 9.81 131.27 7.28 123.35 7.38L119.93 7.38C119.93 7.38 116.03 7.38 115.75 7.38Z",
        ),
        cuts = listOf(
            "M97.96 127.22C97.4 127.21 96.91 127.61 96.81 128.17C96.72 128.73 97.05 129.27 97.59 129.44L115.99 135.71C116.02 135.73 116.05 135.75 116.08 135.76L119.14 136.56C119.33 136.61 119.53 136.61 119.72 136.56L122.79 135.76C122.82 135.75 122.85 135.73 122.87 135.71L141.26 129.44C141.8 129.27 142.13 128.73 142.05 128.17C141.95 127.62 141.47 127.21 140.9 127.22ZM104.81 129.5L134.05 129.5L122.17 133.55L119.43 134.26L116.68 133.55Z",
        ),
        mark = "GAME BOY ADVANCE", markY = 25.4f, markSize = 7.2f,
    )

    private val GB_RIDGE_PLAQUE =
        "M57.2 16L182.8 16A24 24 0 0 1 182.8 64L57.2 64A24 24 0 0 1 57.2 16ZM57.2 18.2L182.8 18.2A21.8 21.8 0 0 1 182.8 61.8L57.2 61.8A21.8 21.8 0 0 1 57.2 18.2Z"

    private val GB_CUTS = listOf(
        rect(12.82f, 111.3f, 2.25f, 146.5f),
        rect(224.93f, 111.3f, 2.25f, 146.5f),
        "M101.54 230.29L138.46 230.29L120 251.76ZM104.59 232.49L135.41 232.49L120 249.04Z",
    )

    private val GB_NOTCHED = Spec(
        vw = 240f, vh = 259f,
        outline = "M9.8 0L208.9 0A4.2 4 0 0 1 213.1 4L213.1 10.7A0.8 0.8 0 0 0 213.9 11.5L228.7 11.5A4.8 5 0 0 1 233.5 16.5L233.5 255.6A4.3 3.4 0 0 1 229.2 259L10.8 259A4.3 3.4 0 0 1 6.5 255.6L6.5 2.4A3.3 2.4 0 0 1 9.8 0Z",
        label = RectF(35.3f, 75.4f, 205f, 224.8f),
        ridges = listOf(
            "M6.5 20.7L35.86 20.7L33.52 26.65L6.5 26.65ZM8.75 22.89L33.61 22.89L31.27 24.46L8.75 24.46Z",
            "M233.5 20.7L204.14 20.7L206.48 26.65L233.5 26.65ZM231.25 22.89L206.39 22.89L208.73 24.46L231.25 24.46Z",
            "M6.5 28.48L30.94 28.48L29.79 34.43L6.5 34.43ZM8.75 30.67L28.69 30.67L27.54 32.24L8.75 32.24Z",
            "M233.5 28.48L209.06 28.48L210.21 34.43L233.5 34.43ZM231.25 30.67L211.31 30.67L212.46 32.24L231.25 32.24Z",
            "M6.5 36.25L29.19 36.25L29.19 42.2L6.5 42.2ZM8.75 38.44L26.94 38.44L26.94 40.01L8.75 40.01Z",
            "M233.5 36.25L210.81 36.25L210.81 42.2L233.5 42.2ZM231.25 38.44L213.06 38.44L213.06 40.01L231.25 40.01Z",
            "M6.5 44.03L29.79 44.03L30.94 49.98L6.5 49.98ZM8.75 46.22L27.54 46.22L28.69 47.79L8.75 47.79Z",
            "M233.5 44.03L210.21 44.03L209.06 49.98L233.5 49.98ZM231.25 46.22L212.46 46.22L211.31 47.79L231.25 47.79Z",
            "M6.5 51.8L33.52 51.8L35.86 57.75L6.5 57.75ZM8.75 53.99L31.27 53.99L33.61 55.56L8.75 55.56Z",
            "M233.5 51.8L206.48 51.8L204.14 57.75L233.5 57.75ZM231.25 53.99L208.73 53.99L206.39 55.56L231.25 55.56Z",
            GB_RIDGE_PLAQUE,
        ),
        cuts = GB_CUTS,
        mark = "Nintendo GAME BOY", markY = 44.6f, markSize = 11f,
    )

    private val GB_ROUNDED = Spec(
        vw = 240f, vh = 259f,
        outline = "M24.5 0L215.5 0A18 18 0 0 1 233.5 18L233.5 255.6A4.3 3.4 0 0 1 229.2 259L10.8 259A4.3 3.4 0 0 1 6.5 255.6L6.5 18A18 18 0 0 1 24.5 0Z",
        label = RectF(35.3f, 75.4f, 205f, 224.8f),
        ridges = (0 until 5).flatMap { i ->
            val y = 20.7f + i * 7.775f
            listOf(rect(6.5f, y, 7f, 5.95f), rect(226.5f, y, 7f, 5.95f))
        } + GB_RIDGE_PLAQUE,
        cuts = GB_CUTS,
        mark = "GAME BOY COLOR", markY = 44.6f, markSize = 11f,
        topGloss = true,
    )

    private val NES = Spec(
        vw = 240f, vh = 268f,
        outline = "M8 0H232A8 8 0 0 1 240 8V260A8 8 0 0 1 232 268H8A8 8 0 0 1 0 260V8A8 8 0 0 1 8 0Z",
        label = RectF(28f, 20f, 212f, 152f),
        cuts = (0 until 8).map { i -> rect(18f, 170f + i * 11.5f, 204f, 4.2f) } +
            rect(4f, 8f, 232f, 1.6f),
    )

    private val SNES = Spec(
        vw = 240f, vh = 206f,
        outline = "M10 0H230A10 10 0 0 1 240 10V178L232 184V202A4 4 0 0 1 228 206H12A4 4 0 0 1 8 202V184L0 178V10A10 10 0 0 1 10 0Z",
        label = RectF(30f, 32f, 210f, 160f),
        cuts = listOf(rect(22f, 9f, 196f, 3f), rect(22f, 17f, 196f, 3f)),
        mark = "SUPER NINTENDO", markY = 176f, markSize = 9.5f,
    )

    private val DS = Spec(
        vw = 240f, vh = 226f,
        outline = "M12 0H198L240 42V214A12 12 0 0 1 228 226H12A12 12 0 0 1 0 214V12A12 12 0 0 1 12 0Z",
        label = RectF(18f, 52f, 222f, 214f),
        cuts = (0 until 6).map { i -> rect(20f + i * 9f, 12f, 4f, 26f) },
        mark = "NINTENDO DS", markX = 138f, markY = 33f, markSize = 11f,
    )

    fun spec(shape: CartShape): Spec = when (shape) {
        CartShape.GBA -> GBA
        CartShape.GB_NOTCHED -> GB_NOTCHED
        CartShape.GB_ROUNDED -> GB_ROUNDED
        CartShape.NES -> NES
        CartShape.SNES -> SNES
        CartShape.DS -> DS
    }

    fun render(cart: Cart, widthPx: Int, typeface: Typeface): Bitmap {
        val spec = spec(cart.shape)
        val outline = spec.outline
        val s = widthPx / spec.vw
        val bmp = createBitmap(widthPx, ceil(spec.vh * s).toInt())
        val c = Canvas(bmp)
        c.scale(s, s)

        val base = cart.shell.rgb or 0xFF000000.toInt()
        val clear = cart.shell.finish == Finish.CLEAR
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        c.withClip(outline) {
        if (clear) drawBoard(c, spec)
        fill.color = if (clear) (base and 0x00FFFFFF) or (0xB8 shl 24) else base
        c.drawPath(outline, fill)
        if (cart.shell.finish == Finish.GLITTER) drawGlitter(c, spec, cart.key.hashCode())

        // Moulded plastic catches light at the top and falls off toward the contacts.
        fill.shader = LinearGradient(
            0f, 0f, 0f, spec.vh,
            intArrayOf(0x2EFFFFFF, 0x00FFFFFF, 0x38000000),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, spec.vw, spec.vh, fill)
        fill.shader = null

        if (spec.topGloss) drawTopGloss(c)
        spec.ridges.forEach { emboss(c, it, raised = true) }
        spec.cuts.forEach { emboss(c, it, raised = false) }
        spec.mark?.let { drawMark(c, spec, it, typeface, base) }

        // Label recess, then the label.
        val recess = RectF(spec.label).apply { inset(-2.6f, -2.6f) }
        fill.color = darken(base, 0.62f)
        c.drawRoundRect(recess, 2.4f, 2.4f, fill)
        drawLabel(c, spec.label, cart, typeface, s)
        fill.shader = LinearGradient(
            0f, spec.label.top, 0f, spec.label.bottom,
            intArrayOf(0x1CFFFFFF, 0x00FFFFFF, 0x14000000),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(spec.label, fill)
        fill.shader = null
        }

        // A hairline around the silhouette so dark plastic still reads on black.
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
            color = lighten(base, 0.16f) and 0x00FFFFFF or (0x90 shl 24)
        }
        c.drawPath(outline, edge)
        return bmp
    }

    private fun emboss(c: Canvas, path: Path, raised: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val o = if (raised) -1f else 1f
        p.color = 0x2AFFFFFF
        c.withTranslation(o, o) { drawPath(path, p) }
        p.color = 0x5C000000
        c.drawPath(path, p)
    }

    private fun drawTopGloss(c: Canvas) {
        val gloss = Path().apply {
            moveTo(13.5f, 18f); arcTo(13.5f, 7f, 35.5f, 29f, 180f, 90f, false)
            lineTo(215.5f, 7f); arcTo(204.5f, 7f, 226.5f, 29f, 270f, 90f, false)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        listOf(10f to 0x4D, 7f to 0x47, 4.5f to 0x47, 2.2f to 0x40).forEach { (w, a) ->
            p.strokeWidth = w
            p.color = (a shl 24) or 0xFFFFFF
            c.drawPath(gloss, p)
        }
    }

    private fun drawMark(c: Canvas, spec: Spec, text: String, typeface: Typeface, base: Int) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = spec.markSize
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        p.color = 0x30FFFFFF
        c.drawText(text, spec.markX + 0.5f, spec.markY + 0.5f, p)
        p.color = darken(base, 0.7f) and 0x00FFFFFF or (0xD0 shl 24)
        c.drawText(text, spec.markX, spec.markY, p)
    }

    /** Clear plastic shows the board through it. */
    private fun drawBoard(c: Canvas, spec: Spec) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val board = RectF(spec.vw * 0.08f, spec.vh * 0.1f, spec.vw * 0.92f, spec.vh)
        p.color = 0xFF1F5C3A.toInt()
        c.drawRect(board, p)
        p.color = 0xFF2E7A4E.toInt()
        p.strokeWidth = 1.2f
        var x = board.left + 10f
        while (x < board.right - 6f) {
            c.drawLine(x, board.top + 8f, x, board.bottom - 14f, p)
            x += 9f
        }
        p.color = 0xFFD4A93C.toInt()
        x = board.left + 6f
        while (x < board.right - 6f) {
            c.drawRect(x, board.bottom - 10f, x + 3.2f, board.bottom, p)
            x += 6f
        }
        p.color = 0xFF151515.toInt()
        c.drawRect(spec.vw * 0.36f, spec.vh * 0.5f, spec.vw * 0.64f, spec.vh * 0.72f, p)
    }

    private fun drawGlitter(c: Canvas, spec: Spec, seed: Int) {
        val rnd = java.util.Random(seed.toLong())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat((spec.vw * spec.vh / 22f).toInt()) {
            p.color = if (rnd.nextBoolean()) 0x55FFFFFF else 0x30FFFFFF
            c.drawCircle(rnd.nextFloat() * spec.vw, rnd.nextFloat() * spec.vh, 0.25f + rnd.nextFloat() * 0.45f, p)
        }
    }

    private fun drawLabel(c: Canvas, r: RectF, cart: Cart, typeface: Typeface, scale: Float) {
        val art = cart.label?.let { decodeLabel(it, (r.width() * scale).roundToInt(), (r.height() * scale).roundToInt(), cart.fit) }
        if (art != null) {
            c.drawBitmap(art, null, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            art.recycle()
        } else {
            generatedLabel(c, r, cart, typeface)
        }
    }

    /** No scan for this cart: print one, in a colour drawn from its title, like slot does. */
    private fun generatedLabel(c: Canvas, r: RectF, cart: Cart, typeface: Typeface) {
        val bg = labelColour(cart.title)
        c.drawRect(r, Paint().apply { color = bg })

        val ink = if (luma(bg) > 0.6f) 0xFF15151A.toInt() else 0xFFF6F4EF.toInt()
        val pad = r.width() * 0.08f
        val width = (r.width() - 2 * pad).toInt()
        val maxH = r.height() * 0.62f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            color = ink
        }
        var size = r.height() * 0.2f
        var layout: StaticLayout
        while (true) {
            paint.textSize = size
            layout = StaticLayout.Builder.obtain(cart.title, 0, cart.title.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 0.92f)
                .setMaxLines(3)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            if (layout.height <= maxH || size <= r.height() * 0.075f) break
            size *= 0.9f
        }
        c.withTranslation(r.left + pad, r.top + (r.height() * 0.84f - layout.height) / 2f) {
            layout.draw(this)
        }

        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = r.height() * 0.075f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
            color = ink and 0x00FFFFFF or (0x99 shl 24)
        }
        c.drawText(cart.platform.title.uppercase(), r.centerX(), r.bottom - r.height() * 0.07f, small)
    }

    /**
     * Decodes label art into a w x h panel. [ArtFit.FILL] covers the panel, cropping what spills
     * over a little above centre (box art keeps its logo near the top); [ArtFit.FIT] shows the
     * whole image on its own average colour.
     */
    private fun decodeLabel(file: java.io.File, w: Int, h: Int, fit: ArtFit): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val src = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val out = createBitmap(w, h)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        when (fit) {
            ArtFit.FILL -> {
                val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
                val cw = (w / scale).roundToInt().coerceAtMost(src.width)
                val ch = (h / scale).roundToInt().coerceAtMost(src.height)
                val left = (src.width - cw) / 2
                val top = ((src.height - ch) * FILL_BIAS).roundToInt()
                canvas.drawBitmap(src, Rect(left, top, left + cw, top + ch), Rect(0, 0, w, h), paint)
            }
            ArtFit.FIT -> {
                val average = src.scale(1, 1).let { px -> px[0, 0].also { px.recycle() } }
                canvas.drawColor(average)
                val scale = min(w.toFloat() / src.width, h.toFloat() / src.height)
                val dw = src.width * scale
                val dh = src.height * scale
                canvas.drawBitmap(src, null, RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), paint)
            }
        }
        src.recycle()
        out
    }.getOrNull()

    fun labelColour(title: String): Int {
        val h = title.fold(7) { acc, ch -> acc * 31 + ch.code }
        val hue = ((h ushr 1) % 360).toFloat()
        val sat = 0.38f + ((h ushr 9) % 25) / 100f
        val value = 0.42f + ((h ushr 15) % 30) / 100f
        return Color.HSVToColor(floatArrayOf(hue, sat, value))
    }

    private fun luma(c: Int): Float =
        (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f

    private fun darken(c: Int, k: Float): Int =
        Color.rgb((Color.red(c) * k).toInt(), (Color.green(c) * k).toInt(), (Color.blue(c) * k).toInt())

    private fun lighten(c: Int, k: Float): Int = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * k).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * k).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * k).toInt(),
    )
}
