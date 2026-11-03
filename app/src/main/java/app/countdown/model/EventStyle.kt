package app.countdown.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class FontChoice { SANS, ROUNDED, SERIF, MONO }

enum class WeightChoice(val value: Int) { LIGHT(300), REGULAR(400), SEMIBOLD(600), BOLD(800) }

enum class WidgetFormat { SQUARE, WIDE }

/** Reihenfolge = Zeichenreihenfolge (spätere liegen oben und werden beim Antippen bevorzugt). */
enum class ElementType { STICKER, TITLE, DATE, COUNTDOWN }

enum class Align { START, CENTER, END }

enum class StickerShape { CIRCLE, ROUNDED }

/** Einfarbig (1 Farbe) oder linearer Farbverlauf (2–3 Farben) mit Winkel in Grad (0 = links→rechts). */
data class Fill(val colors: List<Int>, val angle: Float = 90f) {
    init { require(colors.isNotEmpty()) }

    val isGradient: Boolean get() = colors.size > 1
    val first: Int get() = colors.first()

    /** Start- und Endpunkt des Verlaufs in einem Rechteck der Größe w×h (wie CSS linear-gradient). */
    fun points(w: Float, h: Float): FloatArray {
        val rad = Math.toRadians(angle.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val half = (abs(w * dx) + abs(h * dy)) / 2f
        val cx = w / 2f
        val cy = h / 2f
        return floatArrayOf(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half)
    }

    /** Farbe an Position t (0..1) entlang des Verlaufs. */
    fun colorAt(t: Float): Int {
        if (!isGradient) return first
        val p = t.coerceIn(0f, 1f) * (colors.size - 1)
        val i = p.toInt().coerceAtMost(colors.size - 2)
        return lerpColor(colors[i], colors[i + 1], p - i)
    }

    fun toJson(): JSONObject = JSONObject().put("c", JSONArray(colors)).put("a", angle.toDouble())

    companion object {
        fun solid(color: Int) = Fill(listOf(color))
        fun gradient(vararg colors: Int, angle: Float = 90f) = Fill(colors.toList(), angle)

        fun fromJson(o: JSONObject?, fallback: Fill): Fill {
            val arr = o?.optJSONArray("c") ?: return fallback
            val colors = List(arr.length()) { arr.getInt(it) }.take(3)
            return if (colors.isEmpty()) fallback else Fill(colors, o.optDouble("a", 90.0).toFloat())
        }

        fun lerpColor(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = (((a shr shift) and 0xFF) + (((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t).toInt()
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}

/** Position eines Elements im Widget: x = Ankerpunkt je nach [align], y = Oberkante, jeweils 0..1. */
data class Element(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val visible: Boolean = true,
    val align: Align = Align.START,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("x", x.toDouble()).put("y", y.toDouble()).put("s", scale.toDouble())
        .put("v", visible).put("al", align.name)

    companion object {
        fun fromJson(o: JSONObject?, fallback: Element): Element {
            if (o == null) return fallback
            return Element(
                x = o.optDouble("x", fallback.x.toDouble()).toFloat(),
                y = o.optDouble("y", fallback.y.toDouble()).toFloat(),
                scale = o.optDouble("s", 1.0).toFloat(),
                visible = o.optBoolean("v", fallback.visible),
                align = runCatching { Align.valueOf(o.getString("al")) }.getOrDefault(fallback.align),
            )
        }
    }
}

data class WidgetLayout(
    val title: Element,
    val date: Element,
    val countdown: Element,
    val sticker: Element,
) {
    operator fun get(type: ElementType): Element = when (type) {
        ElementType.TITLE -> title
        ElementType.DATE -> date
        ElementType.COUNTDOWN -> countdown
        ElementType.STICKER -> sticker
    }

    fun with(type: ElementType, e: Element): WidgetLayout = when (type) {
        ElementType.TITLE -> copy(title = e)
        ElementType.DATE -> copy(date = e)
        ElementType.COUNTDOWN -> copy(countdown = e)
        ElementType.STICKER -> copy(sticker = e)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        ElementType.entries.forEach { put(it.name, get(it).toJson()) }
    }

    companion object {
        val DefaultSquare = WidgetLayout(
            title = Element(0.10f, 0.11f),
            date = Element(0.10f, 0.25f),
            countdown = Element(0.10f, 0.50f),
            sticker = Element(0.90f, 0.10f, scale = 0.8f, visible = false, align = Align.END),
        )
        val DefaultWide = WidgetLayout(
            title = Element(0.06f, 0.15f),
            date = Element(0.06f, 0.31f),
            countdown = Element(0.06f, 0.50f),
            sticker = Element(0.94f, 0.15f, scale = 1f, visible = false, align = Align.END),
        )

        fun default(format: WidgetFormat) = if (format == WidgetFormat.WIDE) DefaultWide else DefaultSquare

        fun fromJson(o: JSONObject?, fallback: WidgetLayout): WidgetLayout {
            if (o == null) return fallback
            return WidgetLayout(
                title = Element.fromJson(o.optJSONObject("TITLE"), fallback.title),
                date = Element.fromJson(o.optJSONObject("DATE"), fallback.date),
                countdown = Element.fromJson(o.optJSONObject("COUNTDOWN"), fallback.countdown),
                sticker = Element.fromJson(o.optJSONObject("STICKER"), fallback.sticker),
            )
        }
    }
}

/** Gesamter Look eines Events – gilt für die Karte in der App und für die Widgets. */
data class EventStyle(
    val background: Fill = Fill.solid(0xFF0B0B0F.toInt()),
    val backgroundAlpha: Float = 1f,
    val image: String? = null,
    val imageDim: Float = 0.3f,
    val numbers: Fill = Fill.solid(0xFFFFFFFF.toInt()),
    val accent: Fill = Fill.solid(0xFF7D9CC4.toInt()),
    val font: FontChoice = FontChoice.SANS,
    val numberWeight: WeightChoice = WeightChoice.LIGHT,
    val textWeight: WeightChoice = WeightChoice.SEMIBOLD,
    val cornerRadius: Float = 26f,
    val showLabels: Boolean = true,
    val sticker: String? = null,
    val stickerShape: StickerShape = StickerShape.CIRCLE,
    val square: WidgetLayout = WidgetLayout.DefaultSquare,
    val wide: WidgetLayout = WidgetLayout.DefaultWide,
) {
    fun layout(format: WidgetFormat): WidgetLayout = if (format == WidgetFormat.WIDE) wide else square

    fun withLayout(format: WidgetFormat, layout: WidgetLayout): EventStyle =
        if (format == WidgetFormat.WIDE) copy(wide = layout) else copy(square = layout)

    val images: List<String> get() = listOfNotNull(image, sticker)

    fun toJson(): JSONObject = JSONObject().apply {
        put("bg", background.toJson())
        put("bgAlpha", backgroundAlpha.toDouble())
        image?.let { put("img", it) }
        put("imgDim", imageDim.toDouble())
        put("num", numbers.toJson())
        put("acc", accent.toJson())
        put("font", font.name)
        put("numW", numberWeight.name)
        put("txtW", textWeight.name)
        put("radius", cornerRadius.toDouble())
        put("labels", showLabels)
        sticker?.let { put("sticker", it) }
        put("stickerShape", stickerShape.name)
        put("square", square.toJson())
        put("wide", wide.toJson())
    }

    companion object {
        fun fromJson(o: JSONObject): EventStyle {
            val d = EventStyle()
            return EventStyle(
                background = Fill.fromJson(o.optJSONObject("bg"), d.background),
                backgroundAlpha = o.optDouble("bgAlpha", 1.0).toFloat(),
                image = o.optString("img").ifBlank { null },
                imageDim = o.optDouble("imgDim", d.imageDim.toDouble()).toFloat(),
                numbers = Fill.fromJson(o.optJSONObject("num"), d.numbers),
                accent = Fill.fromJson(o.optJSONObject("acc"), d.accent),
                font = enumOr(o.optString("font"), d.font),
                numberWeight = enumOr(o.optString("numW"), d.numberWeight),
                textWeight = enumOr(o.optString("txtW"), d.textWeight),
                cornerRadius = o.optDouble("radius", d.cornerRadius.toDouble()).toFloat(),
                showLabels = o.optBoolean("labels", true),
                sticker = o.optString("sticker").ifBlank { null },
                stickerShape = enumOr(o.optString("stickerShape"), d.stickerShape),
                square = WidgetLayout.fromJson(o.optJSONObject("square"), WidgetLayout.DefaultSquare),
                wide = WidgetLayout.fromJson(o.optJSONObject("wide"), WidgetLayout.DefaultWide),
            )
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
            runCatching { enumValueOf<T>(name!!) }.getOrDefault(fallback)
    }
}
