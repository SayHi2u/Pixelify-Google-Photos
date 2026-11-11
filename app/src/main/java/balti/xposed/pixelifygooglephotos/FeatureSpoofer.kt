package balti.xposed.pixelifygooglephotos

import android.content.SharedPreferences
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_OVERRIDE_ROM_FEATURE_LEVELS
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_FEATURES_LIST
import io.github.libxposed.api.XposedInterface.Chain

/**
 * Decides how the hooked `PackageManager.hasSystemFeature()` enquiries are answered.
 *
 * Migrated from the legacy `IXposedHookLoadPackage` implementation:
 * - Preferences now come from the framework Remote Preferences (injected by [PixelifyModule])
 *   instead of `XSharedPreferences`.
 * - Instead of `XC_MethodHook.beforeHookedMethod` + `param.setResult(...)`, the decision is
 *   expressed as an interceptor return value: `true`/`false` short-circuit the original method,
 *   returning [Chain.proceed] keeps the original result.
 *
 * The spoofing lists are computed once when the class is constructed (process start), so changing
 * the configuration still requires a Google Photos restart - same semantics as the original.
 */
class FeatureSpoofer(prefs: SharedPreferences?) {

    private val verboseLog: Boolean = prefs?.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false) ?: false

    private fun log(message: String) {
        ModuleLog.d(message)
    }

    /**
     * Preference to override upper feature levels from custom ROMs.
     */
    private val overrideCustomROMLevels: Boolean =
        prefs?.getBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true) ?: true

    /**
     * The final list of feature flags to spoof (return `true` for them).
     * Derived from the user-selected feature levels, like the original implementation.
     */
    private val finalFeaturesToSpoof: List<String> = run {
        val defaultFeatures = DeviceProps.defaultFeatures
        val defaultFeatureLevelsName = defaultFeatures.map { it.displayName }.toSet()

        val defaultFeatureFlags = defaultFeatures.flatMap { it.featureFlags }
        val featureFlags = prefs?.getStringSet(PREF_SPOOF_FEATURES_LIST, defaultFeatureLevelsName)?.let { set ->
            val eligibleFeatures: List<DeviceProps.Features> =
                when {
                    set.isEmpty() -> {
                        log("Feature flags init: EMPTY SET")
                        listOf()
                    }
                    set == defaultFeatureLevelsName -> {
                        log("Feature flags init: DEFAULT SET")
                        defaultFeatures
                    }
                    else -> DeviceProps.allFeatures.filter { set.contains(it.displayName) }
                }

            val allFeatureFlags = ArrayList<String>(0)
            eligibleFeatures.forEach { allFeatureFlags.addAll(it.featureFlags) }
            allFeatureFlags
        } ?: defaultFeatureFlags

        featureFlags.apply { log("Pass TRUE for feature flags: $featureFlags") }
    }

    /**
     * Feature flags that must be reported as absent (`false`) when the user selected a lower
     * Pixel feature level than the one the ROM advertises (only if [overrideCustomROMLevels]).
     */
    private val featuresNotToSpoof: List<String> = run {
        val allFeatureFlags = ArrayList<String>(0)
        DeviceProps.allFeatures.map { it.featureFlags }.forEach { allFeatureFlags.addAll(it) }
        allFeatureFlags.filter { it !in finalFeaturesToSpoof }.apply {
            log("Pass FALSE for feature flags: $this")
        }
    }

    /**
     * Interceptor body for both `hasSystemFeature(String)` and `hasSystemFeature(String, int)`.
     * Returns `true`/`false` to spoof the answer, or [Chain.proceed] to leave it untouched.
     */
    fun interceptHasSystemFeature(chain: Chain): Any? {
        // args[0] is always the queried feature name; args[1] (when present) is a version int.
        val feature = chain.args.firstOrNull()?.toString()

        var passFeatureTrue = false
        var passFeatureFalse = false

        if (feature in finalFeaturesToSpoof) passFeatureTrue = true
        else if (overrideCustomROMLevels) {
            if (feature in featuresNotToSpoof) passFeatureFalse = true
        }

        return when {
            passFeatureTrue -> {
                if (verboseLog) log("TRUE - feature args: ${chain.args}")
                true
            }
            passFeatureFalse -> {
                if (verboseLog) log("FALSE - feature args: ${chain.args}")
                false
            }
            else -> {
                if (verboseLog) log("NO_CHANGE - feature args: ${chain.args}")
                chain.proceed()
            }
        }
    }
}
