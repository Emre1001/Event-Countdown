package app.countdown.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class FontChoice { SANS, ROUNDED, SERIF, MONO, GEO, GROTESK, DISPLAY, HAND, TECH }

enum class WeightChoice(val value: Int) { LIGHT(300), REGULAR(400), SEMIBOLD(600), BOLD(800) }

/** Widget-Formate nach Seitenverhältnis. Große Widgets (4×4) nutzen das quadratische Layout. */
enum class WidgetFormat { SQUARE, WIDE, BAR }

/** Reihenfolge = Zeichenreihenfolge (spätere liegen oben); Fotos mit [Element.onTop] liegen über allem. */
enum class ElementType(val rotatable: Boolean) {
    PHOTO1(true), PHOTO2(true), PHOTO3(true),
    TITLE(true), DATE(true), NOTE(true), PROGRESS(false), COUNTDOWN(false);

    val isPhoto get() = this == PHOTO1 || this == PHOTO2 || this == PHOTO3
    val photoIndex get() = when (this) { PHOTO1 -> 0; PHOTO2 -> 1; PHOTO3 -> 2; else -> -1 }

    companion object {
        val photos = listOf(PHOTO1, PHOTO2, PHOTO3)
    }
}

enum class Align { START, CENTER, END }

/** Material einer Fläche oder Schrift: Farbe/Verlauf, Milchglas oder Flüssigglas. */
enum class Material { COLOR, FROST, LIQUID }

/** Animation der Ziffern beim Wechsel (App: je Ziffer; Widget: je Einheit). */
enum class DigitAnimation { ROLL_UP, ROLL_DOWN, FADE, ZOOM, FLIP, MELT, NONE }

/**
 * Einfarbig (1 Farbe), linearer Verlauf (2–3 Farben, Winkel in Grad, 0 = links→rechts) oder
 * Glas (die erste Farbe ist dann der Farbton des Glases).
 */
data class Fill(val colors: List<Int>, val angle: Float = 90f, val material: Material = Material.COLOR) {
    init { require(colors.isNotEmpty()) }

    val isGradient: Boolean get() = material == Material.COLOR && colors.size > 1
    val isGlass: Boolean get() = material != Material.COLOR
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

    fun toJson(): JSONObject = JSONObject().put("c", JSONArray(colors)).put("a", angle.toDouble()).put("m", material.name)

    companion object {
        fun solid(color: Int) = Fill(listOf(color))
        fun gradient(vararg colors: Int, angle: Float = 90f) = Fill(colors.toList(), angle)
        fun glass(tint: Int, material: Material) = Fill(listOf(tint), 90f, material)

        fun fromJson(o: JSONObject?, fallback: Fill): Fill {
            val arr = o?.optJSONArray("c") ?: return fallback
            val colors = List(arr.length()) { arr.getInt(it) }.take(3)
            if (colors.isEmpty()) return fallback
            val m = runCatching { Material.valueOf(o.getString("m")) }.getOrDefault(Material.COLOR)
            return Fill(colors, o.optDouble("a", 90.0).toFloat(), m)
        }

        fun lerpColor(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = (((a shr shift) and 0xFF) + (((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t).toInt()
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}

/** Position eines Elements: x = Ankerpunkt je nach [align], y = Oberkante, jeweils 0..1 des Widgets. */
data class Element(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val visible: Boolean = true,
    val align: Align = Align.START,
    val rotation: Float = 0f,
    val onTop: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("x", x.toDouble()).put("y", y.toDouble()).put("s", scale.toDouble())
        .put("v", visible).put("al", align.name).put("r", rotation.toDouble()).put("top", onTop)

    companion object {
        fun fromJson(o: JSONObject?, fallback: Element): Element {
            if (o == null) return fallback
            return Element(
                x = o.optDouble("x", fallback.x.toDouble()).toFloat(),
                y = o.optDouble("y", fallback.y.toDouble()).toFloat(),
                scale = o.optDouble("s", 1.0).toFloat(),
                visible = o.optBoolean("v", fallback.visible),
                align = runCatching { Align.valueOf(o.getString("al")) }.getOrDefault(fallback.align),
                rotation = o.optDouble("r", 0.0).toFloat(),
                onTop = o.optBoolean("top", false),
            )
        }
    }
}

data class WidgetLayout(val elements: Map<ElementType, Element>) {
    operator fun get(type: ElementType): Element = elements[type] ?: hidden

    fun with(type: ElementType, e: Element): WidgetLayout = WidgetLayout(elements + (type to e))

    fun toJson(): JSONObject = JSONObject().apply { elements.forEach { (k, v) -> put(k.name, v.toJson()) } }

    companion object {
        private val hidden = Element(0.5f, 0.5f, visible = false)

        private val DefaultSquare = WidgetLayout(
            mapOf(
                ElementType.TITLE to Element(0.10f, 0.11f),
                ElementType.DATE to Element(0.10f, 0.25f),
                ElementType.NOTE to Element(0.10f, 0.36f, visible = false),
                ElementType.COUNTDOWN to Element(0.10f, 0.50f),
                ElementType.PROGRESS to Element(0.10f, 0.88f, visible = false),
                ElementType.PHOTO1 to Element(0.90f, 0.10f, scale = 0.8f, visible = false, align = Align.END),
                ElementType.PHOTO2 to Element(0.50f, 0.30f, scale = 0.8f, visible = false, align = Align.CENTER),
                ElementType.PHOTO3 to Element(0.30f, 0.30f, scale = 0.8f, visible = false, align = Align.CENTER),
            ),
        )
        private val DefaultWide = WidgetLayout(
            mapOf(
                ElementType.TITLE to Element(0.06f, 0.15f),
                ElementType.DATE to Element(0.06f, 0.31f),
                ElementType.NOTE to Element(0.94f, 0.31f, visible = false, align = Align.END),
                ElementType.COUNTDOWN to Element(0.06f, 0.50f),
                ElementType.PROGRESS to Element(0.06f, 0.88f, visible = false),
                ElementType.PHOTO1 to Element(0.94f, 0.15f, visible = false, align = Align.END),
                ElementType.PHOTO2 to Element(0.75f, 0.20f, visible = false, align = Align.CENTER),
                ElementType.PHOTO3 to Element(0.55f, 0.20f, visible = false, align = Align.CENTER),
            ),
        )
        private val DefaultBar = WidgetLayout(
            mapOf(
                ElementType.TITLE to Element(0.04f, 0.20f, scale = 1.3f),
                ElementType.DATE to Element(0.04f, 0.58f, scale = 1.3f),
                ElementType.NOTE to Element(0.5f, 0.6f, visible = false, align = Align.CENTER),
                ElementType.COUNTDOWN to Element(0.96f, 0.12f, scale = 1.25f, align = Align.END),
                ElementType.PROGRESS to Element(0.04f, 0.86f, visible = false),
                ElementType.PHOTO1 to Element(0.50f, 0.10f, scale = 1.6f, visible = false, align = Align.CENTER),
                ElementType.PHOTO2 to Element(0.62f, 0.10f, scale = 1.6f, visible = false, align = Align.CENTER),
                ElementType.PHOTO3 to Element(0.38f, 0.10f, scale = 1.6f, visible = false, align = Align.CENTER),
            ),
        )

        fun default(format: WidgetFormat) = when (format) {
            WidgetFormat.SQUARE -> DefaultSquare
            WidgetFormat.WIDE -> DefaultWide
            WidgetFormat.BAR -> DefaultBar
        }

        fun fromJson(o: JSONObject?, format: WidgetFormat): WidgetLayout {
            val d = default(format)
            if (o == null) return d
            val map = ElementType.entries.associateWith { t ->
                // "STICKER" stammt aus Version 0.0.1 und wird zum ersten Foto.
                val key = if (t == ElementType.PHOTO1 && !o.has("PHOTO1") && o.has("STICKER")) "STICKER" else t.name
                Element.fromJson(o.optJSONObject(key), d[t])
            }
            return WidgetLayout(map)
        }
    }
}

/** Ein Foto im Widget: Ecken (0 = eckig, 0.5 = Kreis), Rand mit eigener Farbe/Verlauf/Glas. */
data class Photo(
    val image: String,
    val corner: Float = 0.5f,
    val borderWidth: Float = 0f,
    val border: Fill = Fill.solid(0xFFFFFFFF.toInt()),
    val shadow: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().put("img", image).put("corner", corner.toDouble())
        .put("bw", borderWidth.toDouble()).put("b", border.toJson()).put("sh", shadow)

    companion object {
        fun fromJson(o: JSONObject?): Photo? {
            val img = o?.optString("img")?.ifBlank { null } ?: return null
            return Photo(
                image = img,
                corner = o.optDouble("corner", 0.5).toFloat(),
                borderWidth = o.optDouble("bw", 0.0).toFloat(),
                border = Fill.fromJson(o.optJSONObject("b"), Fill.solid(0xFFFFFFFF.toInt())),
                shadow = o.optBoolean("sh", true),
            )
        }
    }
}

/** Kontur und Schatten für Schrift. */
data class TextEffects(
    val outlineWidth: Float = 0f,
    val outlineColor: Int = 0xFF000000.toInt(),
    val shadow: Boolean = false,
    val shadowColor: Int = 0x99000000.toInt(),
    val shadowBlur: Float = 6f,
) {
    fun toJson(): JSONObject = JSONObject().put("ow", outlineWidth.toDouble()).put("oc", outlineColor)
        .put("sh", shadow).put("sc", shadowColor).put("sb", shadowBlur.toDouble())

    companion object {
        fun fromJson(o: JSONObject?): TextEffects {
            if (o == null) return TextEffects()
            return TextEffects(
                outlineWidth = o.optDouble("ow", 0.0).toFloat(),
                outlineColor = o.optInt("oc", 0xFF000000.toInt()),
                shadow = o.optBoolean("sh", false),
                shadowColor = o.optInt("sc", 0x99000000.toInt()),
                shadowBlur = o.optDouble("sb", 6.0).toFloat(),
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
    val imageBlur: Float = 0f,
    val numbers: Fill = Fill.solid(0xFFFFFFFF.toInt()),
    val accent: Fill = Fill.solid(0xFF7D9CC4.toInt()),
    val font: FontChoice = FontChoice.SANS,
    val numberFont: FontChoice? = null,
    val numberWeight: WeightChoice = WeightChoice.LIGHT,
    val textWeight: WeightChoice = WeightChoice.SEMIBOLD,
    val cornerRadius: Float = 26f,
    val cardInset: Float = 0f,
    val borderWidth: Float = 0f,
    val border: Fill = Fill.solid(0x33FFFFFF),
    val showLabels: Boolean = true,
    val separators: Boolean = false,
    val effects: TextEffects = TextEffects(),
    val animation: DigitAnimation = DigitAnimation.ROLL_UP,
    val photos: List<Photo?> = listOf(null, null, null),
    val square: WidgetLayout = WidgetLayout.default(WidgetFormat.SQUARE),
    val wide: WidgetLayout = WidgetLayout.default(WidgetFormat.WIDE),
    val bar: WidgetLayout = WidgetLayout.default(WidgetFormat.BAR),
) {
    val digitFont: FontChoice get() = numberFont ?: font

    fun layout(format: WidgetFormat): WidgetLayout = when (format) {
        WidgetFormat.SQUARE -> square
        WidgetFormat.WIDE -> wide
        WidgetFormat.BAR -> bar
    }

    fun withLayout(format: WidgetFormat, layout: WidgetLayout): EventStyle = when (format) {
        WidgetFormat.SQUARE -> copy(square = layout)
        WidgetFormat.WIDE -> copy(wide = layout)
        WidgetFormat.BAR -> copy(bar = layout)
    }

    fun photo(i: Int): Photo? = photos.getOrNull(i)

    fun withPhoto(i: Int, p: Photo?): EventStyle =
        copy(photos = List(3) { if (it == i) p else photos.getOrNull(it) })

    val images: List<String> get() = listOfNotNull(image) + photos.mapNotNull { it?.image }

    /** Bilddateien umbenennen (für Import). */
    fun mapImages(map: (String) -> String?): EventStyle = copy(
        image = image?.let(map),
        photos = photos.map { p -> p?.let { q -> map(q.image)?.let { q.copy(image = it) } } },
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("bg", background.toJson())
        put("bgAlpha", backgroundAlpha.toDouble())
        image?.let { put("img", it) }
        put("imgDim", imageDim.toDouble())
        put("imgBlur", imageBlur.toDouble())
        put("num", numbers.toJson())
        put("acc", accent.toJson())
        put("font", font.name)
        numberFont?.let { put("numFont", it.name) }
        put("numW", numberWeight.name)
        put("txtW", textWeight.name)
        put("radius", cornerRadius.toDouble())
        put("inset", cardInset.toDouble())
        put("borderW", borderWidth.toDouble())
        put("border", border.toJson())
        put("labels", showLabels)
        put("sep", separators)
        put("fx", effects.toJson())
        put("anim", animation.name)
        put("photos", JSONArray(photos.map { it?.toJson() ?: JSONObject.NULL }))
        put("square", square.toJson())
        put("wide", wide.toJson())
        put("bar", bar.toJson())
    }

    companion object {
        fun fromJson(o: JSONObject): EventStyle {
            val d = EventStyle()
            val photosJson = o.optJSONArray("photos")
            val photos = MutableList<Photo?>(3) { i -> photosJson?.optJSONObject(i)?.let { Photo.fromJson(it) } }
            // Version 0.0.1: ein einzelnes "sticker"-Bild mit Kreis- oder abgerundeter Form.
            val legacySticker = o.optString("sticker").ifBlank { null }
            if (photos[0] == null && legacySticker != null) {
                photos[0] = Photo(legacySticker, corner = if (o.optString("stickerShape") == "ROUNDED") 0.22f else 0.5f)
            }
            return EventStyle(
                background = Fill.fromJson(o.optJSONObject("bg"), d.background),
                backgroundAlpha = o.optDouble("bgAlpha", 1.0).toFloat(),
                image = o.optString("img").ifBlank { null },
                imageDim = o.optDouble("imgDim", d.imageDim.toDouble()).toFloat(),
                imageBlur = o.optDouble("imgBlur", 0.0).toFloat(),
                numbers = Fill.fromJson(o.optJSONObject("num"), d.numbers),
                accent = Fill.fromJson(o.optJSONObject("acc"), d.accent),
                font = enumOr(o.optString("font"), d.font),
                numberFont = o.optString("numFont").ifBlank { null }?.let { enumOr(it, d.font) },
                numberWeight = enumOr(o.optString("numW"), d.numberWeight),
                textWeight = enumOr(o.optString("txtW"), d.textWeight),
                cornerRadius = o.optDouble("radius", d.cornerRadius.toDouble()).toFloat(),
                cardInset = o.optDouble("inset", 0.0).toFloat(),
                borderWidth = o.optDouble("borderW", 0.0).toFloat(),
                border = Fill.fromJson(o.optJSONObject("border"), d.border),
                showLabels = o.optBoolean("labels", true),
                separators = o.optBoolean("sep", false),
                effects = TextEffects.fromJson(o.optJSONObject("fx")),
                animation = enumOr(o.optString("anim"), d.animation),
                photos = photos,
                square = WidgetLayout.fromJson(o.optJSONObject("square"), WidgetFormat.SQUARE),
                wide = WidgetLayout.fromJson(o.optJSONObject("wide"), WidgetFormat.WIDE),
                bar = WidgetLayout.fromJson(o.optJSONObject("bar"), WidgetFormat.BAR),
            )
        }

        inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
            runCatching { enumValueOf<T>(name!!) }.getOrDefault(fallback)
    }
}
