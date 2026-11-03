package app.countdown.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Kopiert gewählte Bilder verkleinert in den App-Speicher, damit auch Widgets sie lesen können. */
object ImageStore {
    private const val MAX_SIDE = 1280
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 10).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun dir(context: Context) = File(context.filesDir, "images").apply { mkdirs() }

    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = decode(context, uri) ?: return@runCatching null
            val name = UUID.randomUUID().toString() + if (bmp.hasAlpha()) ".png" else ".jpg"
            File(dir(context), name).outputStream().use { out ->
                if (bmp.hasAlpha()) bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                else bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            cache.put(name, bmp)
            name
        }.getOrNull()
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val big = max(info.size.width, info.size.height)
                if (big > MAX_SIDE) {
                    val f = MAX_SIDE.toFloat() / big
                    decoder.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // Canvas-tauglich (kein Hardware-Bitmap)
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    fun load(context: Context, name: String?): Bitmap? {
        if (name.isNullOrBlank()) return null
        cache.get(name)?.let { return it }
        val file = File(dir(context), name)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()?.also { cache.put(name, it) }
    }

    fun delete(context: Context, names: Collection<String>) {
        names.forEach { n ->
            cache.remove(n)
            File(dir(context), n).delete()
        }
    }
}
