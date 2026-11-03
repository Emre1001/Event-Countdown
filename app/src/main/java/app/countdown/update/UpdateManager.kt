package app.countdown.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Prüft GitHub Releases, lädt die APK in den App-Cache und übergibt sie an den Android-Installer. */
class UpdateManager(private val context: Context, private val owner: String, private val repo: String) {

    data class Info(val version: String, val notes: String, val apkUrl: String, val size: Long)

    private val dir get() = File(context.cacheDir, "updates")

    /** Installer-Dateien werden nie aufbewahrt. */
    fun deleteInstallerFiles() { dir.deleteRecursively() }

    fun currentVersion(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** Liefert das neueste Release, falls es neuer ist als die installierte Version. */
    suspend fun check(): Info? = withContext(Dispatchers.IO) {
        val conn = (URL("https://api.github.com/repos/$owner/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "$repo-updater")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conn.responseCode == 404) return@withContext null // noch kein Release veröffentlicht
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").removePrefix("v")
            if (!isNewer(version, currentVersion())) return@withContext null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return@withContext Info(version, json.optString("body"), a.getString("browser_download_url"), a.optLong("size"))
                }
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(info: Info, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        deleteInstallerFiles()
        dir.mkdirs()
        val file = File(dir, "update.apk")
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection // folgt GitHubs Redirect zum CDN
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.size
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (p != last) { last = p; onProgress(p) }
                        }
                    }
                }
            }
            file
        } catch (e: Exception) {
            deleteInstallerFiles()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.', '-').mapNotNull { it.toIntOrNull() }
        val l = local.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val d = r.getOrElse(i) { 0 } - l.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
