package dev.titanslot.core

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Play builds. Google Play doesn't allow apps to download native code from anywhere else, so
 * each core is an on-demand feature module (core_gambatte, core_mgba) that Play delivers when
 * setup asks for it.
 */
object Delivery {
    const val SOURCE = "Google Play"

    /** Lets this process see feature modules downloaded since it started. */
    fun attach(context: Context) {
        SplitCompat.install(context)
    }

    fun installer(activity: ComponentActivity): CoreInstaller = PlayCoreInstaller(activity)
}

private class PlayCoreInstaller(activity: ComponentActivity) : CoreInstaller {
    private val context = activity.applicationContext
    private val manager = SplitInstallManagerFactory.create(context)
    private var declined: (() -> Unit)? = null

    // Play asks before large downloads, or downloads over a metered connection.
    private val confirm = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode != Activity.RESULT_OK) declined?.invoke()
    }

    override fun isInstalled(core: Core): Boolean = CoreFiles.find(context, core) != null

    override suspend fun install(cores: List<Core>, onStatus: (Core, CoreStatus) -> Unit): Boolean {
        val missing = cores.filterNot(::isInstalled)
        if (missing.isEmpty()) return true
        val byModule = missing.associateBy { it.module }
        missing.forEach { onStatus(it, CoreStatus.Waiting) }

        return suspendCancellableCoroutine { cont ->
            var session = 0
            lateinit var listener: SplitInstallStateUpdatedListener

            fun finish(ok: Boolean) {
                manager.unregisterListener(listener)
                declined = null
                if (cont.isActive) cont.resume(ok)
            }

            fun fail(reason: String) {
                missing.forEach { onStatus(it, CoreStatus.Failed(reason)) }
                finish(false)
            }

            fun installed() {
                // Unpacks the new split's libraries so this process can find them now.
                SplitCompat.install(context)
                missing.forEach {
                    onStatus(it, if (isInstalled(it)) CoreStatus.Installed else CoreStatus.Failed("Restart the app to finish"))
                }
                finish(missing.all(::isInstalled))
            }

            listener = SplitInstallStateUpdatedListener { state ->
                if (session != 0 && state.sessionId() != session) return@SplitInstallStateUpdatedListener
                if (state.moduleNames().none { it in byModule }) return@SplitInstallStateUpdatedListener
                val these = state.moduleNames().mapNotNull { byModule[it] }
                when (state.status()) {
                    SplitInstallSessionStatus.PENDING -> these.forEach { onStatus(it, CoreStatus.Waiting) }
                    SplitInstallSessionStatus.DOWNLOADING -> {
                        val total = state.totalBytesToDownload()
                        val fraction = if (total > 0) state.bytesDownloaded().toFloat() / total else null
                        these.forEach { onStatus(it, CoreStatus.Downloading(fraction)) }
                    }
                    SplitInstallSessionStatus.DOWNLOADED, SplitInstallSessionStatus.INSTALLING ->
                        these.forEach { onStatus(it, CoreStatus.Installing) }
                    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                        declined = { fail("Download declined") }
                        manager.startConfirmationDialogForResult(state, confirm)
                    }
                    SplitInstallSessionStatus.INSTALLED -> installed()
                    SplitInstallSessionStatus.FAILED -> fail(reason(state.errorCode()))
                    SplitInstallSessionStatus.CANCELED -> fail("Cancelled")
                    else -> Unit
                }
            }
            manager.registerListener(listener)
            cont.invokeOnCancellation { manager.unregisterListener(listener) }

            val request = SplitInstallRequest.newBuilder()
                .apply { missing.forEach { addModule(it.module) } }
                .build()
            manager.startInstall(request)
                .addOnSuccessListener { id ->
                    session = id
                    // No session means there was nothing left to download.
                    if (id == 0) installed()
                }
                .addOnFailureListener { e ->
                    fail(reason((e as? SplitInstallException)?.errorCode ?: SplitInstallErrorCode.INTERNAL_ERROR))
                }
        }
    }

    private fun reason(code: Int): String = when (code) {
        SplitInstallErrorCode.NETWORK_ERROR -> "No connection"
        SplitInstallErrorCode.INSUFFICIENT_STORAGE -> "Not enough space"
        SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED -> "Another download is running"
        SplitInstallErrorCode.API_NOT_AVAILABLE, SplitInstallErrorCode.PLAY_STORE_NOT_FOUND -> "Google Play isn't available"
        SplitInstallErrorCode.APP_NOT_OWNED -> "Install Titan Slot from Google Play to get its emulators"
        SplitInstallErrorCode.MODULE_UNAVAILABLE -> "Not available for this version"
        else -> "Install failed (error $code)"
    }
}
