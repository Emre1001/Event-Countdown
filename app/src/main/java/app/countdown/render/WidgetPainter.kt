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
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import app.countdown.R
import app.countdown.model.Align
import app.countdown.model.Countdown
import app.countdown.model.CountdownEvent
import app.countdown.model.CountdownUnit
import app.countdown.model.Element
import app.countdown.model.ElementType
import app.countdown.model.EventMode
import app.countdown.model.EventStyle
import app.countdown.model.Fill
import app.countdown.model.FontChoice
import app.countdown.model.Material
import app.countdown.model.Photo
import app.countdown.model.TextEffects
import app.countdown.model.UnitValue
import app.countdown.model.WeightChoice
import app.countdown.model.WidgetFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Zeichnet ein Widget auf einen Canvas – identisch für das echte Widget und für die Vorschau bzw.
 * den Layout-Editor in der App. Größen beziehen sich auf die Widget-Höhe.
 */
object WidgetPainter {

    /** Wo die tickenden Widget-Sekunden (TextClock) liegen müssen. */
    data class SecondsSlot(
        val left: Float,
        val top: Float,
        val textSizePx: Float,
        val color: Int,
        val countdown: Boolean,
        /** Oberkante und Breite (2 Ziffern) mit der normalen Zahlen-Schrift – für den Chronometer (Android 12+). */
        val plainTop: Float = top,
        val digitsWidth: Float = 0f,
    )

    /** Eine Zahl als eigene Ebene (Widget: je Einheit animierbar). */
    data class NumberLayer(
        val unit: CountdownUnit,
        val text: String,
        val x: Float,
        val baseline: Float,
        val size: Float,
        val area: RectF,
        val bounds: RectF,
    )

    /** Widget-Ebenen: Grundbild (Karte + Elemente), darüber die Zahlen, ganz oben Elemente "im Vordergrund". */
    enum class Layer { ALL, BASE, TOP }

    /** Ob irgendein sichtbares Element im Vordergrund liegt (dann braucht das Widget die obere Ebene). */
    fun hasTopLayer(event: CountdownEvent, format: WidgetFormat): Boolean =
        event.style.layout(format).elements.values.any { it.visible && it.onTop }

    class Result {
        val bounds = LinkedHashMap<ElementType, RectF>()
        var seconds: SecondsSlot? = null
        val numbers = ArrayList<NumberLayer>()
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    private const val NUMBER = 0.30f
    private const val LABEL = 0.075f
    private const val TITLE = 0.105f
    private const val DATE = 0.075f
    private const val NOTE = 0.07f
    private const val PHOTO = 0.36f

    /**
     * @param numberLayers true im Widget: Zahlen werden nicht gezeichnet, sondern als [Result.numbers]
     *   gemeldet (eigene, animierte Ebenen); Sekunden kommen dann vom TextClock ([Result.seconds]).
     * @param placeholders true im Layout-Editor: Platzhalter für Fotos ohne Bild.
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
        locale: Locale,
        numberLayers: Boolean = false,
        result: Result? = null,
        placeholders: Boolean = false,
        layer: Layer = Layer.ALL,
    ) {
        result?.bounds?.clear()
        result?.seconds = null
        result?.numbers?.clear()
        val style = event.style
        val density = context.resources.displayMetrics.density
        val card = cardRect(w, h, style)
        val radius = min(style.cornerRadius * density, min(card.width(), card.height()) / 2f)
        if (layer != Layer.TOP) drawCard(context, canvas, card, style, density, radius)

        val layout = style.layout(format)
        val margin = 0.07f * min(w, h)
        val target = event.effectiveTarget(now)
        val remaining = Countdown.remaining(target, now, event.widgetUnits)

        for (pass in 0..1) {
            if (layer == Layer.BASE && pass == 1 || layer == Layer.TOP && pass == 0) continue
            for (type in ElementType.entries) {
                val e = layout[type]
                if (!e.visible || (e.onTop) != (pass == 1)) continue
                when {
                    type.isPhoto -> {
                        val photo = style.photo(type.photoIndex)
                        if (photo == null && !placeholders) continue
                        val size = PHOTO * h * e.scale
                        val r = place(e.x * w, e.y * h, size, size, e.align, w, h, loose = true)
                        rotated(canvas, r, e.rotation) { drawPhoto(context, canvas, photo, r, density) }
                        result?.bounds?.put(type, r)
                    }
                    type == ElementType.TITLE || type == ElementType.DATE || type == ElementType.NOTE -> {
                        val label = when (type) {
                            ElementType.TITLE -> event.title.ifBlank { context.getString(R.string.untitled) }
                            ElementType.DATE -> dateLine(context, event, target, now, locale)
                            else -> event.note.ifBlank { if (placeholders) context.getString(R.string.note_placeholder) else "" }
                        }
                        if (label.isBlank()) continue
                        val r = drawLabel(context, canvas, label, type, e, style, w, h, margin)
                        result?.bounds?.put(type, r)
                    }
                    type == ElementType.PROGRESS -> {
                        val r = drawProgress(context, canvas, event, target, now, e, w, h, margin, density)
                        result?.bounds?.put(type, r)
                    }
                    type == ElementType.COUNTDOWN -> {
                        val r = drawCountdown(context, canvas, style, remaining.values, remaining.isPast, e, w, h, margin, numberLayers, result)
                        result?.bounds?.put(type, r)
                    }
                }
            }
        }
    }

    fun cardRect(w: Float, h: Float, style: EventStyle): RectF {
        val inset = style.cardInset.coerceIn(0f, 0.3f) * min(w, h)
        return RectF(inset, inset, w - inset, h - inset)
    }

    private fun dateLine(context: Context, event: CountdownEvent, target: Long, now: Long, locale: Locale): String = when {
        event.mode == EventMode.SINCE -> context.getString(R.string.since_prefix) + " " + Countdown.formatDay(event.targetMillis, locale)
        target < now -> context.getString(R.string.elapsed) + " · " + Countdown.formatDate(target, locale)
        else -> Countdown.formatDate(target, locale)
    }

    // --- Karte / Hintergrund ----------------------------------------------------------------------

    /** Hintergrund des Events (auch für die App-Karte): Farbe, Verlauf, Foto, Milch- oder Flüssigglas, Rand. */
    @Synchronized
    fun drawCard(context: Context, canvas: Canvas, r: RectF, style: EventStyle, density: Float, radius: Float) {
        val alpha = (style.backgroundAlpha.coerceIn(0f, 1f) * 255).toInt()
        val mat = style.background.material
        val image = style.image

        if (style.cardInset >= 0.02f && mat == Material.COLOR && alpha > 200) {
            resetFill()
            fill.color = Color.argb(alpha, 0, 0, 0)
            fill.setShadowLayer(14 * density, 0f, 5 * density, Color.argb(90, 0, 0, 0))
            canvas.drawRoundRect(r, radius, radius, fill)
        }
        resetFill()
        if (image != null) {
            val blur = when (mat) {
                Material.FROST -> max(style.imageBlur, 22f)
                Material.LIQUID -> max(style.imageBlur, 6f)
                Material.COLOR -> style.imageBlur
            } * density
            val bmp = Glass.blurredImage(context, image, blur)
            if (bmp != null) {
                fill.shader = cropShader(bmp, r)
                fill.alpha = alpha
                canvas.drawRoundRect(r, radius, radius, fill)
                resetFill()
                fill.color = Color.argb((style.imageDim * alpha).toInt().coerceIn(0, 255), 0, 0, 0)
                canvas.drawRoundRect(r, radius, radius, fill)
                resetFill()
            }
        }
        when (mat) {
            Material.COLOR -> if (image == null) {
                applyFill(fill, style.background, r)
                fill.alpha = alpha
                canvas.drawRoundRect(r, radius, radius, fill)
            }
            Material.FROST -> drawFrost(canvas, r, radius, style.background.first, alpha, density)
            Material.LIQUID -> drawLiquid(canvas, r, radius, style.background.first, alpha, density)
        }
        resetFill()

        val bw = style.borderWidth * density
        if (bw > 0f) {
            val inner = RectF(r).apply { inset(bw / 2f, bw / 2f) }
            strokeFill(fill, style.border, inner, bw)
            canvas.drawRoundRect(inner, max(0f, radius - bw / 2f), max(0f, radius - bw / 2f), fill)
            resetFill()
        } else if (mat != Material.COLOR) {
            // Glas ohne eigenen Rand: feine Lichtkante.
            val sw = 1.2f * density
            val inner = RectF(r).apply { inset(sw / 2f, sw / 2f) }
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = sw
            fill.shader = LinearGradient(
                r.left, r.top, r.right, r.bottom,
                intArrayOf(Color.argb(170, 255, 255, 255), Color.argb(30, 255, 255, 255), Color.argb(110, 255, 255, 255)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(inner, radius, radius, fill)
            resetFill()
        }
    }

    private fun drawFrost(canvas: Canvas, r: RectF, radius: Float, tint: Int, alpha: Int, density: Float) {
        resetFill()
        fill.color = withAlpha(tint, (alpha * 0.46f).toInt())
        canvas.drawRoundRect(r, radius, radius, fill)
        fill.color = Color.WHITE
        fill.shader = LinearGradient(0f, r.top, 0f, r.bottom, Color.argb((alpha * 0.16f).toInt(), 255, 255, 255), Color.argb((alpha * 0.03f).toInt(), 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(r, radius, radius, fill)
        fill.shader = Glass.noise
        fill.alpha = alpha
        canvas.drawRoundRect(r, radius, radius, fill)
        resetFill()
    }

    private fun drawLiquid(canvas: Canvas, r: RectF, radius: Float, tint: Int, alpha: Int, density: Float) {
        resetFill()
        fill.color = withAlpha(tint, (alpha * 0.18f).toInt())
        canvas.drawRoundRect(r, radius, radius, fill)
        // Spiegelung oben links und weiches Leuchten am unteren Rand – wie gebogenes Glas.
        fill.color = Color.WHITE
        fill.shader = RadialGradient(
            r.left + r.width() * 0.22f, r.top, max(r.width(), r.height()) * 0.9f,
            intArrayOf(Color.argb((alpha * 0.34f).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255)),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(r, radius, radius, fill)
        fill.shader = LinearGradient(0f, r.bottom, 0f, r.bottom - r.height() * 0.45f, Color.argb((alpha * 0.14f).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(r, radius, radius, fill)
        // Innere Kante: dunkle, dünne Linie für Tiefe.
        resetFill()
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = 3f * density
        fill.color = Color.argb((alpha * 0.10f).toInt(), 0, 0, 0)
        val inner = RectF(r).apply { inset(2.5f * density, 2.5f * density) }
        canvas.drawRoundRect(inner, max(0f, radius - 2.5f * density), max(0f, radius - 2.5f * density), fill)
        resetFill()
    }

    // --- Text ---------------------------------------------------------------------------------------

    private fun drawLabel(
        context: Context, canvas: Canvas, label: String, type: ElementType, e: Element,
        style: EventStyle, w: Float, h: Float, margin: Float,
    ): RectF {
        val (size, weight) = when (type) {
            ElementType.TITLE -> TITLE to style.textWeight
            ElementType.DATE -> DATE to WeightChoice.REGULAR
            else -> NOTE to WeightChoice.REGULAR
        }
        text.reset()
        text.flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        text.typeface = Fonts.typeface(context, style.font, weight)
        text.textSize = size * h * e.scale
        val shown = TextUtils.ellipsize(label, text, w - 2 * margin, TextUtils.TruncateAt.END).toString()
        val fm = text.fontMetrics
        val tw = text.measureText(shown)
        val r = place(e.x * w, e.y * h, tw, fm.descent - fm.ascent, e.align, w, h)
        rotated(canvas, r, e.rotation) {
            drawStyledText(canvas, shown, r.left, r.top - fm.ascent, text, style.accent, r, style.effects, if (type == ElementType.TITLE) 255 else 200)
        }
        return r
    }

    /**
     * Text mit Füllung (Farbe, Verlauf, Glas), optionaler Kontur und Schatten.
     * [p] muss Schrift und Größe bereits enthalten.
     */
    fun drawStyledText(
        canvas: Canvas, s: String, x: Float, baseline: Float, p: TextPaint,
        f: Fill, box: RectF, fx: TextEffects, alpha: Int = 255,
    ) {
        val stroke = p.textSize * fx.outlineWidth * 0.045f
        val blur = max(1f, p.textSize * fx.shadowBlur * 0.012f)
        if (stroke > 0f) {
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeJoin = Paint.Join.ROUND
            p.strokeWidth = stroke * 2f
            p.color = fx.outlineColor
            p.alpha = (Color.alpha(fx.outlineColor) * alpha / 255)
            if (fx.shadow) p.setShadowLayer(blur, 0f, blur * 0.35f, fx.shadowColor) else p.clearShadowLayer()
            canvas.drawText(s, x, baseline, p)
            p.style = Paint.Style.FILL
            p.strokeWidth = 0f
        }
        p.clearShadowLayer()
        if (fx.shadow && stroke <= 0f) p.setShadowLayer(blur, 0f, blur * 0.35f, fx.shadowColor)
        when (f.material) {
            Material.COLOR -> {
                applyFill(p, f, box)
                p.alpha = alpha
                canvas.drawText(s, x, baseline, p)
            }
            Material.FROST -> {
                p.shader = null
                p.color = withAlpha(Fill.lerpColor(f.first, Color.WHITE, 0.55f), alpha * 215 / 255)
                canvas.drawText(s, x, baseline, p)
                p.clearShadowLayer()
                p.shader = Glass.noise
                canvas.drawText(s, x, baseline, p)
                p.shader = null
            }
            Material.LIQUID -> {
                p.shader = null
                p.color = withAlpha(f.first, alpha * 110 / 255)
                canvas.drawText(s, x, baseline, p)
                p.clearShadowLayer()
                p.color = Color.WHITE
                p.shader = LinearGradient(0f, box.top, 0f, box.top + box.height() * 0.62f, Color.argb(alpha * 200 / 255, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
                canvas.drawText(s, x, baseline, p)
                p.shader = null
                p.style = Paint.Style.STROKE
                p.strokeWidth = max(1f, p.textSize * 0.018f)
                p.color = Color.argb(alpha * 170 / 255, 255, 255, 255)
                canvas.drawText(s, x, baseline, p)
                p.style = Paint.Style.FILL
                p.strokeWidth = 0f
            }
        }
        p.clearShadowLayer()
        p.shader = null
    }

    // --- Fortschritt ---------------------------------------------------------------------------------

    private fun drawProgress(
        context: Context, canvas: Canvas, event: CountdownEvent, target: Long, now: Long,
        e: Element, w: Float, h: Float, margin: Float, density: Float,
    ): RectF {
        val style = event.style
        val value = if (event.mode == EventMode.SINCE) {
            // "Seit": Fortschritt bis zum nächsten Jahrestag.
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(event.targetMillis).atZone(zone)
            var years = java.time.temporal.ChronoUnit.YEARS.between(start, Instant.ofEpochMilli(now).atZone(zone))
            if (years < 0) years = 0
            val last = start.plusYears(years).toInstant().toEpochMilli()
            val next = start.plusYears(years + 1).toInstant().toEpochMilli()
            Countdown.progress(last, next, now)
        } else {
            Countdown.progress(min(event.startMillis, target), target, now)
        }
        val barH = max(3f * density, 0.045f * h * e.scale)
        val barW = min(w - 2 * margin, 0.62f * w * e.scale)
        text.reset()
        text.flags = Paint.ANTI_ALIAS_FLAG
        text.typeface = Fonts.typeface(context, style.font, style.textWeight)
        text.textSize = 0.07f * h * e.scale
        val pct = "${(value * 100).toInt()} %"
        val pw = if (style.showLabels) text.measureText(pct) + barH * 2 else 0f
        val fm = text.fontMetrics
        val totalH = max(barH, fm.descent - fm.ascent)
        val r = place(e.x * w, e.y * h, barW + pw, totalH, e.align, w, h)
        val bar = RectF(r.left, r.centerY() - barH / 2f, r.left + barW, r.centerY() + barH / 2f)
        resetFill()
        fill.color = withAlpha(style.accent.first, 70)
        canvas.drawRoundRect(bar, barH / 2f, barH / 2f, fill)
        if (value > 0f) {
            val done = RectF(bar.left, bar.top, bar.left + max(barH, bar.width() * value), bar.bottom)
            if (style.numbers.material == Material.COLOR) applyFill(fill, style.numbers, bar)
            else { fill.shader = null; fill.color = Fill.lerpColor(style.numbers.first, Color.WHITE, 0.4f) }
            canvas.drawRoundRect(done, barH / 2f, barH / 2f, fill)
        }
        resetFill()
        if (style.showLabels) {
            drawStyledText(canvas, pct, bar.right + barH * 2, r.centerY() - (fm.ascent + fm.descent) / 2f, text, style.accent, r, style.effects, 210)
        }
        return r
    }

    // --- Countdown ------------------------------------------------------------------------------------

    private fun numberPaint(context: Context, style: EventStyle) = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Fonts.typeface(context, style.digitFont, style.numberWeight)
    }

    private fun drawCountdown(
        context: Context, canvas: Canvas, style: EventStyle, values: List<UnitValue>, past: Boolean,
        e: Element, w: Float, h: Float, margin: Float, layers: Boolean, result: Result?,
    ): RectF {
        val numPaint = numberPaint(context, style)
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Fonts.typeface(context, style.font, style.textWeight)
        }
        val numbers = values.mapIndexed { i, uv -> if (i == 0) uv.value.toString() else uv.value.toString().padStart(2, '0') }
        val labels = values.map { context.getString(shortLabel(it.unit)) }

        var numberSize = NUMBER * h * e.scale
        var labelSize = LABEL * h * e.scale

        fun gap() = if (style.separators) numberSize * 0.42f else numberSize * 0.30f
        fun widths(): Pair<FloatArray, Float> {
            numPaint.textSize = numberSize
            labelPaint.textSize = labelSize
            val cols = FloatArray(values.size) { i ->
                max(numPaint.measureText(numbers[i]), if (style.showLabels) labelPaint.measureText(labels[i]) else 0f)
            }
            return cols to (cols.sum() + gap() * (values.size - 1))
        }

        var (cols, total) = widths()
        val avail = w - 2 * margin
        if (total > avail) {
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
        val area = RectF(r.left, r.top, r.right, r.top + numH)
        val baseline = r.top - nfm.ascent

        var x = r.left
        values.forEachIndexed { i, uv ->
            val box = RectF(x, r.top, x + numPaint.measureText(numbers[i]), r.top + numH)
            when {
                uv.unit == CountdownUnit.SECONDS && layers -> {
                    val secFace = Fonts.secondsTypeface(context, style.digitFont, style.numberWeight, countdown = !past)
                    val sp = Paint().apply { typeface = secFace; textSize = numberSize }
                    val t = (x + cols[i] / 2f - r.left) / max(1f, r.width())
                    result?.seconds = SecondsSlot(
                        left = x,
                        top = baseline + sp.fontMetrics.ascent,
                        textSizePx = numberSize,
                        color = if (style.numbers.material == Material.COLOR) style.numbers.colorAt(gradientT(style.numbers, t))
                        else Fill.lerpColor(style.numbers.first, Color.WHITE, 0.55f),
                        countdown = !past,
                        plainTop = baseline + nfm.ascent,
                        digitsWidth = numPaint.apply { textSize = numberSize }.measureText("00"),
                    )
                }
                layers -> result?.numbers?.add(NumberLayer(uv.unit, numbers[i], x, baseline, numberSize, area, box))
                else -> drawNumber(context, canvas, style, numbers[i], x, baseline, numberSize, area)
            }
            if (style.showLabels) {
                labelPaint.textSize = labelSize
                val lfm2 = labelPaint.fontMetrics
                val lbox = RectF(x, r.top + numH + labelGap, x + cols[i], r.bottom)
                drawStyledText(canvas, labels[i], x, r.top + numH + labelGap - lfm2.ascent, labelPaint, style.accent, lbox, style.effects)
            }
            if (style.separators && i < values.lastIndex) {
                numPaint.textSize = numberSize
                val cx = x + cols[i] + gap() / 2f
                val sw = numPaint.measureText(":")
                val sepBox = RectF(cx - sw / 2f, r.top, cx + sw / 2f, r.top + numH)
                drawStyledText(canvas, ":", cx - sw / 2f, baseline - numH * 0.04f, numPaint, style.accent, sepBox, style.effects, 190)
            }
            x += cols[i] + gap()
        }
        return r
    }

    /** Eine Zahl so zeichnen wie im Widget (für die Vorschau und für die einzelnen Widget-Ebenen). */
    fun drawNumber(context: Context, canvas: Canvas, style: EventStyle, s: String, x: Float, baseline: Float, size: Float, area: RectF) {
        val p = numberPaint(context, style)
        p.textSize = size
        drawStyledText(canvas, s, x, baseline, p, style.numbers, area, style.effects)
    }

    /** Rand um eine Zahl für Kontur und Schatten. */
    fun numberPadding(style: EventStyle, size: Float): Float {
        val fx = style.effects
        val stroke = size * fx.outlineWidth * 0.045f * 2f
        val shadow = if (fx.shadow) size * fx.shadowBlur * 0.012f * 2.5f else 0f
        return stroke + shadow + 2f
    }

    /** Eine Zahl als eigenes kleines Bild (Widget-Ebene); Position: bounds.left/top minus Rand. */
    fun renderNumberLayer(context: Context, style: EventStyle, layer: NumberLayer): Bitmap {
        val pad = numberPadding(style, layer.size)
        val bw = (layer.bounds.width() + 2 * pad).toInt().coerceAtLeast(1)
        val bh = (layer.bounds.height() + 2 * pad).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(pad - layer.bounds.left, pad - layer.bounds.top)
        synchronized(this) { drawNumber(context, c, style, layer.text, layer.x, layer.baseline, layer.size, layer.area) }
        return bmp
    }

    private fun gradientT(fill: Fill, tx: Float): Float {
        val c = kotlin.math.cos(Math.toRadians(fill.angle.toDouble())).toFloat()
        return (0.5f + (tx - 0.5f) * c).coerceIn(0f, 1f)
    }

    fun shortLabel(unit: CountdownUnit) = when (unit) {
        CountdownUnit.YEARS -> R.string.short_years
        CountdownUnit.MONTHS -> R.string.short_months
        CountdownUnit.WEEKS -> R.string.short_weeks
        CountdownUnit.DAYS -> R.string.short_days
        CountdownUnit.HOURS -> R.string.short_hours
        CountdownUnit.MINUTES -> R.string.short_minutes
        CountdownUnit.SECONDS -> R.string.short_seconds
    }

    // --- Fotos -------------------------------------------------------------------------------------------

    private fun drawPhoto(context: Context, canvas: Canvas, photo: Photo?, r: RectF, density: Float) {
        val img = photo?.let { ImageStore.load(context, it.image) }
        val corner = (photo?.corner ?: 0.5f).coerceIn(0f, 0.5f) * min(r.width(), r.height())
        resetFill()
        if (img == null) {
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = 1.5f * density
            fill.color = Color.argb(160, 255, 255, 255)
            fill.pathEffect = DashPathEffect(floatArrayOf(6 * density, 5 * density), 0f)
            val inset = RectF(r).apply { inset(fill.strokeWidth, fill.strokeWidth) }
            canvas.drawRoundRect(inset, corner, corner, fill)
            resetFill()
            return
        }
        if (photo.shadow) {
            fill.color = Color.BLACK
            fill.setShadowLayer(10 * density, 0f, 4 * density, Color.argb(110, 0, 0, 0))
            canvas.drawRoundRect(r, corner, corner, fill)
            resetFill()
        }
        fill.shader = cropShader(img, r)
        canvas.drawRoundRect(r, corner, corner, fill)
        resetFill()
        val bw = photo.borderWidth * density
        if (bw > 0f) {
            val inner = RectF(r).apply { inset(bw / 2f, bw / 2f) }
            strokeFill(fill, photo.border, inner, bw)
            val c = max(0f, corner - bw / 2f)
            canvas.drawRoundRect(inner, c, c, fill)
            resetFill()
        }
    }

    // --- Hilfen ------------------------------------------------------------------------------------------

    private inline fun rotated(canvas: Canvas, r: RectF, degrees: Float, block: () -> Unit) {
        if (degrees == 0f) { block(); return }
        canvas.save()
        canvas.rotate(degrees, r.centerX(), r.centerY())
        block()
        canvas.restore()
    }

    /**
     * Rechteck am Ankerpunkt platzieren. Normal bleibt es ganz im Widget; [loose] (Fotos) darf bis
     * zur Hälfte über den Rand hinausragen.
     */
    private fun place(ax: Float, top: Float, ew: Float, eh: Float, align: Align, w: Float, h: Float, loose: Boolean = false): RectF {
        var left = when (align) {
            Align.START -> ax
            Align.CENTER -> ax - ew / 2f
            Align.END -> ax - ew
        }
        var t = top
        if (loose) {
            left = left.coerceIn(-ew / 2f, w - ew / 2f)
            t = t.coerceIn(-eh / 2f, h - eh / 2f)
        } else {
            left = if (ew >= w) 0f else left.coerceIn(0f, w - ew)
            t = if (eh >= h) 0f else t.coerceIn(0f, h - eh)
        }
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

    /** Rand-Pinsel: Farbe/Verlauf oder bei Glas eine helle Lichtkante. */
    private fun strokeFill(p: Paint, f: Fill, r: RectF, width: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        if (f.isGlass) {
            p.color = Color.WHITE
            p.shader = LinearGradient(
                r.left, r.top, r.right, r.bottom,
                intArrayOf(withAlpha(Fill.lerpColor(f.first, Color.WHITE, 0.7f), 220), withAlpha(f.first, 60), withAlpha(Color.WHITE, 170)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        } else {
            applyFill(p, f, r)
        }
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

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

    /** Platzhalter, solange einem Widget noch kein Event zugeordnet ist. */
    @Synchronized
    fun paintPlaceholder(context: Context, canvas: Canvas, w: Float, h: Float, label: String) {
        val density = context.resources.displayMetrics.density
        resetFill()
        fill.color = 0xFF16161A.toInt()
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 24 * density, 24 * density, fill)
        text.reset()
        text.flags = Paint.ANTI_ALIAS_FLAG
        text.typeface = Fonts.typeface(context, FontChoice.SANS, WeightChoice.SEMIBOLD)
        text.textSize = 0.11f * h
        text.color = 0xFFEDEDEF.toInt()
        canvas.drawText(label, 0.1f * w, 0.18f * h - text.fontMetrics.ascent, text)
    }

    /** Übersichts-Widget: die nächsten Events untereinander, jeweils mit Farbe, Titel und Restzeit. */
    @Synchronized
    fun paintOverview(context: Context, canvas: Canvas, w: Float, h: Float, events: List<CountdownEvent>, now: Long, title: String, empty: String) {
        val density = context.resources.displayMetrics.density
        val r = RectF(0f, 0f, w, h)
        val radius = 26 * density
        drawFrost(canvas, r, radius, 0xFF0E0E11.toInt(), 235, density)
        resetFill()
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = density
        fill.color = Color.argb(40, 255, 255, 255)
        canvas.drawRoundRect(RectF(r).apply { inset(density / 2, density / 2) }, radius, radius, fill)
        resetFill()

        val pad = 16 * density
        text.reset()
        text.flags = Paint.ANTI_ALIAS_FLAG
        text.typeface = Fonts.typeface(context, FontChoice.SANS, WeightChoice.SEMIBOLD)
        text.textSize = 13 * density
        text.color = 0xFF9A9AA2.toInt()
        var y = pad - text.fontMetrics.ascent
        canvas.drawText(title, pad, y, text)
        y += text.fontMetrics.descent + 8 * density

        if (events.isEmpty()) {
            text.color = 0xFFEDEDEF.toInt()
            text.textSize = 15 * density
            canvas.drawText(empty, pad, y - text.fontMetrics.ascent, text)
            return
        }
        val rowH = 30 * density
        val rows = max(1, ((h - y - pad / 2) / rowH).toInt())
        for (e in events.take(rows)) {
            val target = e.effectiveTarget(now)
            val rem = Countdown.remaining(target, now, listOf(CountdownUnit.DAYS, CountdownUnit.HOURS))
            val days = rem.values[0].value
            val value = if (days > 0) "$days ${context.getString(R.string.short_days)}" else "${rem.values[1].value} ${context.getString(R.string.short_hours)}"
            val cy = y + rowH / 2f
            resetFill()
            fill.color = e.style.accent.first or 0xFF000000.toInt()
            canvas.drawCircle(pad + 5 * density, cy, 5 * density, fill)
            text.typeface = Fonts.typeface(context, e.style.font, WeightChoice.SEMIBOLD)
            text.textSize = 15 * density
            text.color = 0xFFEDEDEF.toInt()
            val valueFont = Fonts.typeface(context, e.style.digitFont, WeightChoice.REGULAR)
            val vp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = valueFont; textSize = 17 * density; color = 0xFFFFFFFF.toInt() }
            val vw = vp.measureText(value)
            val titleMax = w - pad * 2 - 18 * density - vw - 12 * density
            val t = TextUtils.ellipsize(e.title.ifBlank { context.getString(R.string.untitled) }, text, titleMax, TextUtils.TruncateAt.END).toString()
            val mid = cy - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2f
            canvas.drawText(t, pad + 18 * density, mid, text)
            if (e.mode == EventMode.SINCE || rem.isPast) vp.color = 0xFF9A9AA2.toInt()
            canvas.drawText(value, w - pad - vw, cy - (vp.fontMetrics.ascent + vp.fontMetrics.descent) / 2f, vp)
            y += rowH
        }
    }
}
