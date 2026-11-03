package app.countdown.render

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import app.countdown.model.FontChoice
import app.countdown.model.WeightChoice

/** Gebündelte Schriften (Inter, Nunito, Fraunces, JetBrains Mono) für Canvas und Compose. */
object Fonts {
    private val cache = HashMap<Int, Typeface>()

    private fun load(context: Context, res: Int): Typeface = synchronized(cache) {
        cache.getOrPut(res) {
            runCatching { ResourcesCompat.getFont(context.applicationContext, res) }.getOrNull() ?: Typeface.DEFAULT
        }
    }

    fun typeface(context: Context, font: FontChoice, weight: WeightChoice): Typeface =
        load(context, FontRes.full(font, weight))

    fun secondsTypeface(context: Context, font: FontChoice, weight: WeightChoice, countdown: Boolean): Typeface =
        load(context, FontRes.seconds(font, weight, countdown))

    private val families = HashMap<FontChoice, FontFamily>()

    fun family(font: FontChoice): FontFamily = synchronized(families) {
        families.getOrPut(font) {
            FontFamily(WeightChoice.entries.map { Font(FontRes.full(font, it), FontWeight(it.value)) })
        }
    }

    fun weight(w: WeightChoice) = FontWeight(w.value)
}
