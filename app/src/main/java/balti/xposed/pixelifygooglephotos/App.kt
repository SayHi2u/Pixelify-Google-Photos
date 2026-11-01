package balti.xposed.pixelifygooglephotos

import android.app.Application

/**
 * Application entry of the module UI process.
 *
 * Registers the Xposed framework service listener as early as possible (process start), matching
 * the official libxposed example layout. The framework pushes its service into this process only
 * while the process is starting, so early registration maximizes the chance that the connection
 * is already established when the first activity appears. See [ModulePrefs].
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        ModulePrefs.connect()
    }
}
