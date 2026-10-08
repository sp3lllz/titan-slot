package dev.titanslot.core

import android.content.Context
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.zip.ZipFile

/**
 * Direct builds, installed from an APK rather than Google Play. Setup downloads each core
 * from the libretro buildbot (the same builds scripts/fetch-cores.sh fetches) into the app's
 * private files, where they can be loaded.
 */
object Delivery {
    const val SOURCE = "libretro's buildbot"

    fun attach(context: Context) = Unit

    fun installer(activity: ComponentActivity): CoreInstaller = DownloadCoreInstaller(activity.applicationContext)
}

private class DownloadCoreInstaller(private val context: Context) : CoreInstaller {

    override fun isInstalled(core: Core): Boolean = CoreFiles.find(context, core) != null

    override suspend fun install(cores: List<Core>, onStatus: (Core, CoreStatus) -> Unit): Boolean {
        var ok = true
        for (core in cores) {
            if (isInstalled(core)) {
                onStatus(core, CoreStatus.Installed)
                continue
            }
            onStatus(core, CoreStatus.Downloading(null))
            val error = withContext(Dispatchers.IO) {
                runCatching { download(core) { onStatus(core, CoreStatus.Downloading(it)) } }.exceptionOrNull()
            }
            if (error == null) {
                onStatus(core, CoreStatus.Installed)
            } else {
                ok = false
                val reason = if (error is UnknownHostException) "No connection" else error.message ?: "Download failed"
                onStatus(core, CoreStatus.Failed(reason))
            }
        }
        return ok
    }

    private fun download(core: Core, progress: (Float) -> Unit) {
        val zip = File(context.cacheDir, "${core.id}_libretro_android.so.zip")
        val dir = CoreFiles.dir(context).apply { mkdirs() }
        val tmp = File(dir, core.library + ".tmp")
        try {
            val conn = URL("$BUILDBOT/${core.id}_libretro_android.so.zip").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "TitanSlot/1.0 (Android)")
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Download failed (HTTP ${conn.responseCode})")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    zip.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        var read = 0L
                        var reported = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0 && read - reported >= total / 40) {
                                reported = read
                                progress(read.toFloat() / total)
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            ZipFile(zip).use { z ->
                val entry = z.entries().asSequence().firstOrNull { !it.isDirectory && it.name.endsWith(".so") }
                    ?: throw IOException("No core in the download")
                z.getInputStream(entry).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            }
            val header = ByteArray(20)
            val got = tmp.inputStream().use { input ->
                var n = 0
                while (n < header.size) {
                    val r = input.read(header, n, header.size - n)
                    if (r < 0) break
                    n += r
                }
                n
            }
            if (got < header.size || !CoreFiles.isArm64Elf(header)) throw IOException("That download isn't an arm64 core")
            if (!tmp.renameTo(File(dir, core.library))) throw IOException("Could not install ${core.title}")
        } finally {
            tmp.delete()
            zip.delete()
        }
    }

    private companion object {
        const val BUILDBOT = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"
    }
}
