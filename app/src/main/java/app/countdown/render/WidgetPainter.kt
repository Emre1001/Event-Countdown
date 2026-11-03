package app.countdown.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import app.countdown.R
import app.countdown.model.Align
import app.countdown.model.Countdown
import app.countdown.model.CountdownEvent
import app.countdown.model.CountdownUnit
import app.countdown.model.ElementType
import app.countdown.model.Fill
import app.countdown.model.StickerShape
import app.countdown.model.WidgetFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Zeichnet ein Widget auf einen Canvas – identisch für das echte Widget (Bitmap) und die
 * Vorschau/den Layout-Editor in der App. Größen beziehen sich auf die Widget-Höhe.
 */
object WidgetPainter {

    /** Wo die tickenden Widget-Sekunden (TextClock) liegen müssen. */
    data class SecondsSlot(val left: Float, val top: Float, val textSizePx: Float, val color: Int, val countdown: Boolean)

    class Result {
        val bounds = LinkedHashMap<ElementType, RectF>()
        var seconds: SecondsSlot? = null
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    private const val NUMBER = 0.30f
    private const val LABEL = 0.075f
    private const val TITLE = 0.105f
    private const val DATE = 0.075f
    private const val STICKER = 0.36f

    /**
     * @param drawSeconds false für das Widget: Sekunden zeichnet dort der TextClock (siehe [Result.seconds]).
     * @param placeholders true im Layout-Editor: zeigt einen Platzhalter für ein Bild ohne Datei.
     */
    @Synchronized
    fun paint(
        context: Context,
        canvas: Canvas,
        w: Float,
        h: Float,
        event: CountdownEvent,
        format: WidgetFormat,
        now: Long,
        drawSeconds: Boolean,
        locale: Locale,
        result: Result? = null,
        placeholders: Boolean = false,
    ) {
        result?.bounds?.clear()
        result?.seconds = null
        val style = event.style
        val density = context.resources.displayMetrics.density
        val radius = min(style.cornerRadius * density, min(w, h) / 2f)
        val card = RectF(0f, 0f, w, h)

        // Hintergrund (Bild oder Farbe/Verlauf) mit weichen, kantengeglätteten Ecken.
        val bg = ImageStore.load(context, style.image)
        resetFill()
        if (bg != null) {
            fill.shader = cropShader(bg, card)
            fill.alpha = (style.backgroundAlpha * 255).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(card, radius, radius, fill)
            resetFill()
            fill.color = Color.argb((style.imageDim * style.backgroundAlpha * 255).toInt().coerceIn(0, 255), 0, 0, 0)
            canvas.drawRoundRect(card, radius, radius, fill)
        } else {
            applyFill(fill, style.background, card)
            fill.alpha = (style.backgroundAlpha * 255).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(card, radius, radius, fill)
        }
        resetFill()

        val layout = style.layout(format)
        val margin = 0.07f * min(w, h)
        val past = event.targetMillis < now
        val remaining = Countdown.remaining(event.targetMillis, now, event.widgetUnits)

        for (type in ElementType.entries) {
            val e = layout[type]
            if (!e.visible) continue
            when (type) {
                ElementType.STICKER -> {
                    val img = ImageStore.load(context, style.sticker)
                    if (img == null && !placeholders) continue
                    val size = STICKER * h * e.scale
                    val r = place(e.x * w, e.y * h, size, size, e.align, w, h)
                    if (img != null) {
                        fill.shader = cropShader(img, r)
                        if (style.stickerShape == StickerShape.CIRCLE) canvas.drawOval(r, fill)
                        else canvas.drawRoundRect(r, size * 0.22f, size * 0.22f, fill)
                    } else {
                        fill.style = Paint.Style.STROKE
                        fill.strokeWidth = 1.5f * density
                        fill.color = Color.argb(150, 255, 255, 255)
                        fill.pathEffect = DashPathEffect(floatArrayOf(6 * density, 5 * density), 0f)
                        val inset = RectF(r).apply { inset(fill.strokeWidth, fill.strokeWidth) }
                        if (style.stickerShape == StickerShape.CIRCLE) canvas.drawOval(inset, fill)
                        else canvas.drawRoundRect(inset, size * 0.22f, size * 0.22f, fill)
                    }
                    resetFill()
                    result?.bounds?.put(type, r)
                }

                ElementType.TITLE, ElementType.DATE -> {
                    val isTitle = type == ElementType.TITLE
                    val label = if (isTitle) {
                        event.title.ifBlank { context.getString(R.string.untitled) }
                    } else {
                        val d = Countdown.formatDate(event.targetMillis, locale)
                        if (past) context.getString(R.string.elapsed) + " · " + d else d
                    }
                    text.reset()
                    text.flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
                    text.typeface = Fonts.typeface(context, style.font, if (isTitle) style.textWeight else app.countdown.model.WeightChoice.REGULAR)
                    text.textSize = (if (isTitle) TITLE else DATE) * h * e.scale
                    val shown = TextUtils.ellipsize(label, text, w - 2 * margin, TextUtils.TruncateAt.END).toString()
                    val fm = text.fontMetrics
                    val tw = text.measureText(shown)
                    val th = fm.descent - fm.ascent
                    val r = place(e.x * w, e.y * h, tw, th, e.align, w, h)
                    applyFill(text, style.accent, r)
                    if (!isTitle) text.alpha = 190
                    canvas.drawText(shown, r.left, r.top - fm.ascent, text)
                    result?.bounds?.put(type, r)
                }

                ElementType.COUNTDOWN -> {
                    val r = drawCountdown(context, canvas, event, remaining.values, past, e, w, h, margin, drawSeconds, result)
                    result?.bounds?.put(type, r)
                }
            }
        }
    }

    private fun drawCountdown(
        context: Context,
        canvas: Canvas,
        event: CountdownEvent,
        values: List<app.countdown.model.UnitValue>,
        past: Boolean,
        e: app.countdown.model.Element,
        w: Float,
        h: Float,
        margin: Float,
        drawSeconds: Boolean,
        result: Result?,
    ): RectF {
        val style = event.style
        val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Fonts.typeface(context, style.font, style.numberWeight)
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Fonts.typeface(context, style.font, style.textWeight)
        }
        val numbers = values.mapIndexed { i, uv -> if (i == 0) uv.value.toString() else uv.value.toString().padStart(2, '0') }
        val labels = values.map { context.getString(shortLabel(it.unit)) }

        var numberSize = NUMBER * h * e.scale
        var labelSize = LABEL * h * e.scale

        fun widths(): Pair<FloatArray, Float> {
            numPaint.textSize = numberSize
            labelPaint.textSize = labelSize
            val cols = FloatArray(values.size) { i ->
                max(numPaint.measureText(numbers[i]), if (style.showLabels) labelPaint.measureText(labels[i]) else 0f)
            }
            return cols to (cols.sum() + numberSize * 0.30f * (values.size - 1))
        }

        var (cols, total) = widths()
        val avail = w - 2 * margin
        if (total > avail) { // passend verkleinern statt abschneiden
            val f = avail / total
            numberSize *= f
            labelSize *= f
            val again = widths()
            cols = again.first
            total = again.second
        }
        val nfm = numPaint.fontMetrics
        val lfm = labelPaint.fontMetrics
        val numH = nfm.descent - nfm.ascent
        val labelGap = numberSize * 0.02f
        val height = numH + if (style.showLabels) labelGap + (lfm.descent - lfm.ascent) else 0f
        val r = place(e.x * w, e.y * h, total, height, e.align, w, h)

        val numRect = RectF(r.left, r.top, r.right, r.top + numH)
        applyFill(numPaint, style.numbers, numRect)
        applyFill(labelPaint, style.accent, r)

        var x = r.left
        val baseline = r.top - nfm.ascent
        values.forEachIndexed { i, uv ->
            if (uv.unit == CountdownUnit.SECONDS && !drawSeconds) {
                val secFace = Fonts.secondsTypeface(context, style.font, style.numberWeight, countdown = !past)
                val sp = Paint().apply { typeface = secFace; textSize = numberSize }
                val t = ((x + cols[i] / 2f - r.left) / max(1f, r.width()))
                result?.seconds = SecondsSlot(
                    left = x,
                    top = baseline + sp.fontMetrics.ascent,
                    textSizePx = numberSize,
                    color = style.numbers.colorAt(gradientT(style.numbers, t)),
                    countdown = !past,
                )
            } else {
                canvas.drawText(numbers[i], x, baseline, numPaint)
            }
            if (style.showLabels) {
                canvas.drawText(labels[i], x, r.top + numH + labelGap - lfm.ascent, labelPaint)
            }
            x += cols[i] + numberSize * 0.30f
        }
        return r
    }

    /** Näherung: Position entlang des Verlaufs für horizontale Anteile (für die TextClock-Farbe). */
    private fun gradientT(fill: Fill, tx: Float): Float {
        val rad = Math.toRadians(fill.angle.toDouble())
        val c = kotlin.math.cos(rad).toFloat()
        val t = 0.5f + (tx - 0.5f) * c
        return t.coerceIn(0f, 1f)
    }

    fun shortLabel(unit: CountdownUnit) = when (unit) {
        CountdownUnit.DAYS -> R.string.short_days
        CountdownUnit.HOURS -> R.string.short_hours
        CountdownUnit.MINUTES -> R.string.short_minutes
        CountdownUnit.SECONDS -> R.string.short_seconds
    }

    /** Rechteck am Ankerpunkt platzieren und vollständig im Widget halten. */
    private fun place(ax: Float, top: Float, ew: Float, eh: Float, align: Align, w: Float, h: Float): RectF {
        var left = when (align) {
            Align.START -> ax
            Align.CENTER -> ax - ew / 2f
            Align.END -> ax - ew
        }
        left = if (ew >= w) 0f else left.coerceIn(0f, w - ew)
        val t = if (eh >= h) 0f else top.coerceIn(0f, h - eh)
        return RectF(left, t, left + ew, t + eh)
    }

    private fun applyFill(p: Paint, f: Fill, r: RectF) {
        if (f.isGradient) {
            val pts = f.points(r.width(), r.height())
            p.shader = LinearGradient(
                r.left + pts[0], r.top + pts[1], r.left + pts[2], r.top + pts[3],
                f.colors.toIntArray(), null, Shader.TileMode.CLAMP,
            )
            p.color = Color.WHITE
        } else {
            p.shader = null
            p.color = f.first
        }
    }

    private fun cropShader(bmp: Bitmap, dst: RectF): BitmapShader {
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = max(dst.width() / bmp.width, dst.height() / bmp.height)
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate(dst.left + (dst.width() - bmp.width * scale) / 2f, dst.top + (dst.height() - bmp.height * scale) / 2f)
        shader.setLocalMatrix(m)
        return shader
    }

    private fun resetFill() {
        fill.reset()
        fill.flags = Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    }

    /** Einfacher Platzhalter, solange einem Widget noch kein Event zugeordnet ist. */
    fun paintPlaceholder(context: Context, canvas: Canvas, w: Float, h: Float, label: String) {
        val density = context.resources.displayMetrics.density
        val r = RectF(0f, 0f, w, h)
        resetFill()
        fill.color = 0xFF16161A.toInt()
        canvas.drawRoundRect(r, 24 * density, 24 * density, fill)
        text.reset()
        text.flags = Paint.ANTI_ALIAS_FLAG
        text.typeface = Fonts.typeface(context, app.countdown.model.FontChoice.SANS, app.countdown.model.WeightChoice.SEMIBOLD)
        text.textSize = 0.11f * h
        text.color = 0xFFEDEDEF.toInt()
        val fm = text.fontMetrics
        canvas.drawText(label, 0.1f * w, 0.18f * h - fm.ascent, text)
    }
}
