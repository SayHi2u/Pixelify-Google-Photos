package balti.xposed.pixelifygooglephotos

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bridge between the module UI (this app process) and the Xposed framework.
 *
 * How it works (Modern Xposed API + libxposed/service):
 * - When this app process is started, the Xposed framework pushes its binder into the process
 *   through the `XposedProvider` that ships inside the `io.github.libxposed:service` library
 *   (merged into our AndroidManifest as `<applicationId>.XposedService`).
 * - [XposedServiceHelper.registerListener] delivers the [XposedService] to this process.
 * - [XposedService.getRemotePreferences] returns a full [SharedPreferences] implementation backed
 *   by the framework ("Remote Preferences"), which is the SAME store that the hooked Google
 *   Photos process reads via `XposedModule.getRemotePreferences()`.
 *
 * This replaces the legacy `getSharedPreferences(name, MODE_WORLD_READABLE)` mechanism that is
 * rejected on modern Android and is not supported by the Modern Xposed API anymore.
 *
 * The connection is asynchronous: the binder may arrive before or after the first activity is
 * created. [current] is null until the service is bound; activities subscribe via
 * [addConnectionListener] to refresh once the connection is established.
 */
object ModulePrefs {

    private const val TAG = "PixelifyGooglePhotos"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var connectStarted = false

    private val connectionListeners = CopyOnWriteArrayList<() -> Unit>()

    /** The framework-backed preferences of this process, or null while not connected. */
    val current: SharedPreferences?
        get() = prefs

    /**
     * Start listening for the framework service. Idempotent; call once from an activity.
     * Safe to call before the framework push arrives: [XposedServiceHelper] caches an early
     * binder and delivers it as soon as a listener is registered.
     */
    @Synchronized
    fun connect() {
        if (connectStarted) return
        connectStarted = true
        try {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    Log.i(TAG, "Framework service bound: ${service.frameworkName} ${service.frameworkVersion}")
                    prefs = try {
                        service.getRemotePreferences(Constants.PREFS_GROUP)
                    } catch (t: Throwable) {
                        Log.e(TAG, "getRemotePreferences failed (framework lacks remote capability?)", t)
                        null
                    }
                    if (prefs != null) notifyConnected()
                }

                override fun onServiceDied(service: XposedService) {
                    Log.w(TAG, "Framework service died")
                    prefs = null
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register Xposed service listener", t)
            connectStarted = false // allow a retry on the next connect() call
        }
    }

    /**
     * Register a listener invoked once when the framework connection becomes available.
     * If already connected, the listener is invoked immediately.
     */
    fun addConnectionListener(listener: () -> Unit) {
        if (prefs != null) {
            listener()
            return
        }
        connectionListeners.add(listener)
    }

    fun removeConnectionListener(listener: () -> Unit) {
        connectionListeners.remove(listener)
    }

    private fun notifyConnected() {
        connectionListeners.forEach { it() }
        connectionListeners.clear()
    }
}
