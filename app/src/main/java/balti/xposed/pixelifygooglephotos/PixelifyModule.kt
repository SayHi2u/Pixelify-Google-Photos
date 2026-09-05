package balti.xposed.pixelifygooglephotos

import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Modern Xposed API (libxposed, API 101/102) module entry.
 *
 * Declared in `META-INF/xposed/java_init.list`. The framework instantiates this class in every
 * hooked process (by default only Google Photos, see `META-INF/xposed/scope.list`) and drives it
 * through the lifecycle callbacks below.
 *
 * Migration notes vs. the legacy implementation:
 * - Legacy `IXposedHookLoadPackage.handleLoadPackage()` is replaced by `onPackageLoaded()`
 *   (default classloader ready, before the app code runs) with `onPackageReady()` as fallback.
 * - Legacy `XposedHelpers.findAndHookMethod(...)` + `XC_MethodHook.beforeHookedMethod` is replaced
 *   by the interceptor-chain API: `hook(Executable).intercept { chain -> ... }`.
 * - Legacy `XposedHelpers.setStaticObjectField` is replaced by plain reflection.
 * - Legacy `XSharedPreferences` is replaced by framework Remote Preferences
 *   (`getRemotePreferences(PREFS_GROUP)`), which work without any world-readable file or root.
 */
class PixelifyModule : XposedModule() {

    private val hooksInstalled = AtomicBoolean(false)

    /**
     * Remote Preferences shared with the module UI process. `null` only if the framework does not
     * provide remote preferences; all readers fall back to their defaults in that case.
     */
    private val prefs: SharedPreferences? by lazy {
        try {
            getRemotePreferences(Constants.PREFS_GROUP)
        } catch (t: Throwable) {
            Log.e(TAG, "Remote preferences unavailable: $t")
            null
        }
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        Log.i(TAG, "Module loaded in process '${param.processName}' (framework $frameworkName, API $apiVersion)")
    }

    /**
     * Best hooking point: the package's default classloader is ready but no application code
     * (not even AppComponentFactory) has run yet. Android Q+ only.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!isSpoofTarget(param.packageName)) return
        Log.i(TAG, "onPackageLoaded: ${param.packageName}")
        installHooks(param.defaultClassLoader)
    }

    /**
     * Fallback hooking point for pre-Q devices where the framework does not deliver
     * [onPackageLoaded] (no default classloader concept).
     */
    override fun onPackageReady(param: PackageReadyParam) {
        if (!isSpoofTarget(param.packageName)) return
        Log.i(TAG, "onPackageReady: ${param.packageName}")
        installHooks(param.classLoader)
    }

    /**
     * Same behaviour as the legacy preference PREF_STRICTLY_CHECK_GOOGLE_PHOTOS: when enabled
     * (default) only hook Google Photos, even if the user adds more packages to the scope.
     */
    private fun isSpoofTarget(packageName: String): Boolean {
        val strictlyGooglePhotos = prefs?.getBoolean(Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) ?: true
        return !(strictlyGooglePhotos && packageName != Constants.PACKAGE_NAME_GOOGLE_PHOTOS)
    }

    private fun installHooks(classLoader: ClassLoader) {
        if (!hooksInstalled.compareAndSet(false, true)) return
        try {
            val featureSpoofer = FeatureSpoofer(prefs)
            val deviceSpoofer = DeviceSpoofer(prefs)

            // android.app.ApplicationPackageManager is a framework (boot classpath) class;
            // resolving it through the app classloader returns the same class object.
            val appPackageManagerClass =
                Class.forName(CLASS_APPLICATION_PACKAGE_MANAGER, false, classLoader)

            // hasSystemFeature(String)
            val singleArgMethod =
                appPackageManagerClass.getDeclaredMethod(METHOD_HAS_SYSTEM_FEATURE, String::class.java)
            hook(singleArgMethod).intercept { chain -> featureSpoofer.interceptHasSystemFeature(chain) }

            // hasSystemFeature(String, int)
            val dualArgMethod = appPackageManagerClass.getDeclaredMethod(
                METHOD_HAS_SYSTEM_FEATURE, String::class.java, Int::class.java)
            hook(dualArgMethod).intercept { chain -> featureSpoofer.interceptHasSystemFeature(chain) }

            // Rewrite android.os.Build / Build.VERSION static fields of this process.
            deviceSpoofer.applyBuildProps()

            Log.i(TAG, "Hooks installed: $CLASS_APPLICATION_PACKAGE_MANAGER#$METHOD_HAS_SYSTEM_FEATURE (x2), Build props applied")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to install hooks", t)
        }
    }

    private companion object {
        const val TAG = "PixelifyGooglePhotos"
        const val CLASS_APPLICATION_PACKAGE_MANAGER = "android.app.ApplicationPackageManager"
        const val METHOD_HAS_SYSTEM_FEATURE = "hasSystemFeature"
    }
}
