package app.countdown.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.countdown.model.CountdownEvent
import app.countdown.render.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Widgets/Events als Datei teilen: ".countdown" = ZIP mit countdown.json und den Bildern.
 * Beim Import bekommt alles neue Namen; nur erwartete Einträge werden gelesen, mit Größenlimits.
 */
object EventShare {
    private const val APP = "event-countdown"
    private const val FORMAT = 1
    private const val JSON_NAME = "countdown.json"
    private const val MAX_JSON = 2L * 1024 * 1024
    private const val MAX_IMAGE = 20L * 1024 * 1024
    private val imageName = Regex("[A-Za-z0-9._-]{1,100}")

    suspend fun shareIntent(context: Context, event: CountdownEvent): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val safeTitle = event.title.trim().ifBlank { "Countdown" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "").take(40).trim().ifBlank { "Countdown" }
        val file = File(dir, "$safeTitle.countdown")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            val json = JSONObject().put("app", APP).put("format", FORMAT).put("event", event.toJson())
            zip.putNextEntry(ZipEntry(JSON_NAME))
            zip.write(json.toString().toByteArray())
            zip.closeEntry()
            event.style.images.distinct().forEach { name ->
                val f = ImageStore.file(context, name)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("images/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, file.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Liest eine .countdown-Datei, speichert das Event (neue ID) und liefert es zurück – oder null. */
    suspend fun import(context: Context, uri: Uri): CountdownEvent? = withContext(Dispatchers.IO) {
        val renamed = HashMap<String, String>()
        var json: JSONObject? = null
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entries = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (++entries > 50) break
                        val name = entry.name
                        when {
                            entry.isDirectory -> Unit
                            name == JSON_NAME -> json = JSONObject(String(readLimited(zip, MAX_JSON)))
                            name.startsWith("images/") -> {
                                val original = name.removePrefix("images/")
                                if (imageName.matches(original) && !original.startsWith(".")) {
                                    ImageStore.importStream(context, zip, original.substringAfterLast('.', "jpg"), MAX_IMAGE)
                                        ?.let { renamed[original] = it }
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
        }
        val root = json
        if (root == null || root.optString("app") != APP) {
            ImageStore.delete(context, renamed.values)
            return@withContext null
        }
        val parsed = runCatching { CountdownEvent.fromJson(root.getJSONObject("event")) }.getOrNull()
        if (parsed == null) {
            ImageStore.delete(context, renamed.values)
            return@withContext null
        }
        val event = parsed.copy(id = UUID.randomUUID().toString(), style = parsed.style.mapImages { renamed[it] })
        EventRepository.add(event)
        event
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            require(total <= limit) { "too large" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
