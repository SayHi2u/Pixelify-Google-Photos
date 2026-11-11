package balti.xposed.pixelifygooglephotos

import android.content.SharedPreferences
import android.os.Build
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
            ModuleLog.e("Remote preferences unavailable", t)
            null
        }
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        ModuleLog.attach { priority, tag, message, throwable ->
            if (throwable == null) {
                this@PixelifyModule.log(priority, tag, message)
            } else {
                this@PixelifyModule.log(priority, tag, message, throwable)
            }
        }
        ModuleLog.i(
            "Module loaded in process '${param.processName}' " +
                "(framework $frameworkName, API $apiVersion)"
        )
    }

    /**
     * Best hooking point: the package's default classloader is ready but no application code
     * (not even AppComponentFactory) has run yet. Android Q+ only.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!isSpoofTarget(param.packageName)) return
        ModuleLog.i("onPackageLoaded: ${param.packageName}")
        installHooks(param.defaultClassLoader)
    }

    /**
     * Fallback hooking point for pre-Q devices where the framework does not deliver
     * [onPackageLoaded] (no default classloader concept).
     */
    override fun onPackageReady(param: PackageReadyParam) {
        if (!isSpoofTarget(param.packageName)) return
        ModuleLog.i("onPackageReady: ${param.packageName}")
        installHooks(param.classLoader)
    }

    /**
     * Same behaviour as the legacy preference PREF_STRICTLY_CHECK_GOOGLE_PHOTOS: when enabled
     * (default) only hook Google Photos, even if the user adds more packages to the scope.
     */
    private fun isSpoofTarget(packageName: String): Boolean {
        val strictlyGooglePhotos =
            prefs?.getBoolean(Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) ?: true
        return !(strictlyGooglePhotos && packageName != Constants.PACKAGE_NAME_GOOGLE_PHOTOS)
    }

    private fun installHooks(classLoader: ClassLoader) {
        if (!hooksInstalled.compareAndSet(false, true)) return

        var anyInstalled = false

        // Build props first: if a feature hook fails later, the model spoof is still applied.
        val deviceSpoofer = DeviceSpoofer(prefs)
        val buildResult = try {
            deviceSpoofer.applyBuildProps()
        } catch (t: Throwable) {
            ModuleLog.e("Applying Build props failed", t)
            null
        }
        if (buildResult != null) {
            if (buildResult.applied > 0) anyInstalled = true
            if (buildResult.failed > 0 || buildResult.versionApplied < buildResult.versionAttempted) {
                ModuleLog.e(
                    "Build props were only partially applied " +
                        "(deviceFailed=${buildResult.failed}, " +
                        "versionFailed=${buildResult.versionAttempted - buildResult.versionApplied})"
                )
            }
        }

        // Feature hooks are installed independently. One failing overload must not prevent
        // the other one (or the Build props above) from working.
        val featureSpoofer = try {
            FeatureSpoofer(prefs)
        } catch (t: Throwable) {
            ModuleLog.e("Creating FeatureSpoofer failed", t)
            null
        }
        val appPackageManagerClass = try {
            Class.forName(CLASS_APPLICATION_PACKAGE_MANAGER, false, classLoader)
        } catch (t: Throwable) {
            ModuleLog.e("ApplicationPackageManager not found", t)
            null
        }
        var featureHookCount = 0
        if (featureSpoofer != null && appPackageManagerClass != null) {
            if (hookHasSystemFeature(appPackageManagerClass, featureSpoofer, String::class.java)) {
                anyInstalled = true
                featureHookCount++
            }
            if (hookHasSystemFeature(
                    appPackageManagerClass,
                    featureSpoofer,
                    String::class.java,
                    Int::class.java,
                )
            ) {
                anyInstalled = true
                featureHookCount++
            }
        }

        if (!anyInstalled) {
            // Nothing could be installed. Allow onPackageReady (or a later callback) to retry.
            hooksInstalled.set(false)
            ModuleLog.e("Neither Build props nor feature hooks could be installed")
        } else {
            ModuleLog.i(
                "Install complete: buildProps=${buildResult?.applied ?: 0}/" +
                    "${buildResult?.attempted ?: 0}, " +
                    "featureHooks=$featureHookCount/2"
            )
        }
    }

    private fun hookHasSystemFeature(
        appPackageManagerClass: Class<*>,
        featureSpoofer: FeatureSpoofer,
        vararg parameterTypes: Class<*>,
    ): Boolean {
        return try {
            val method = appPackageManagerClass.getDeclaredMethod(
                METHOD_HAS_SYSTEM_FEATURE,
                *parameterTypes,
            )
            hook(method).intercept { chain -> featureSpoofer.interceptHasSystemFeature(chain) }
            true
        } catch (t: Throwable) {
            ModuleLog.e(
                "Failed to hook $CLASS_APPLICATION_PACKAGE_MANAGER" +
                    "#$METHOD_HAS_SYSTEM_FEATURE(${parameterTypes.joinToString()})",
                t,
            )
            false
        }
    }

    private companion object {
        const val CLASS_APPLICATION_PACKAGE_MANAGER = "android.app.ApplicationPackageManager"
        const val METHOD_HAS_SYSTEM_FEATURE = "hasSystemFeature"
    }
}
