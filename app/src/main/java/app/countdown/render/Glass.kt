package app.countdown.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.util.LruCache
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Weichzeichnen (für Milchglas über Fotos) und feines Korn für die Glas-Optik. */
object Glass {
    private val blurred = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Verkleinern + dreifacher Box-Blur: schnell und weich genug für Glas. Ergebnis wird gecacht. */
    fun blurredImage(context: Context, name: String, radiusPx: Float): Bitmap? {
        val src = ImageStore.load(context, name) ?: return null
        if (radiusPx < 1f) return src
        val key = "$name@${radiusPx.roundToInt()}"
        blurred.get(key)?.let { return it }
        val factor = 4
        val w = max(1, src.width / factor)
        val h = max(1, src.height / factor)
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val r = max(1, (radiusPx / factor / 2f).roundToInt())
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { boxBlur(px, w, h, r) }
        small.setPixels(px, 0, w, 0, 0, w, h)
        blurred.put(key, small)
        return small
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        pass(px, tmp, w, h, r, horizontal = true)
        pass(tmp, px, w, h, r, horizontal = false)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            var a = 0; var rr = 0; var g = 0; var b = 0
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            for (i in -r..r) {
                val p = src[idx(i)]
                a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (i in 0 until len) {
                dst[idx(i)] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = src[idx(i - r)]
                val inn = src[idx(i + r + 1)]
                a += (inn ushr 24) - (out ushr 24)
                rr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    /** Kachel mit feinem Rauschen – macht Milchglas glaubwürdig, ohne sichtbar zu sein. */
    val noise: BitmapShader by lazy {
        val size = 96
        val rnd = Random(7)
        val px = IntArray(size * size) {
            val v = rnd.nextInt(256)
            (rnd.nextInt(10, 26) shl 24) or (v shl 16) or (v shl 8) or v
        }
        val bmp = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}
