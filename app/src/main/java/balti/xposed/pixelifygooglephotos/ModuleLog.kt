package balti.xposed.pixelifygooglephotos

import android.util.Log

/**
 * Small logging facade used by both the module entry point and the hook helpers.
 *
 * Messages always go to Android logcat. Once [attach] is called from [PixelifyModule],
 * they are also forwarded to the framework log through `XposedInterface.log`, so
 * failures are visible in the LSPosed/Vector log without needing a logcat capture.
 *
 * The framework sink is optional: the UI process and early module loading still work
 * if it has not been attached yet.
 */
object ModuleLog {

    const val TAG = "PixelifyGooglePhotos"

    @Volatile
    private var sink: ((priority: Int, tag: String, message: String, throwable: Throwable?) -> Unit)? = null

    fun attach(sink: (priority: Int, tag: String, message: String, throwable: Throwable?) -> Unit) {
        this.sink = sink
    }

    fun d(message: String) {
        Log.d(TAG, message)
        sink?.invoke(Log.DEBUG, TAG, message, null)
    }

    fun i(message: String) {
        Log.i(TAG, message)
        sink?.invoke(Log.INFO, TAG, message, null)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
        sink?.invoke(Log.WARN, TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        sink?.invoke(Log.ERROR, TAG, message, throwable)
    }
}
