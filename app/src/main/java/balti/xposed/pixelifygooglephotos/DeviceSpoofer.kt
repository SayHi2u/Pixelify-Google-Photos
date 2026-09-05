package balti.xposed.pixelifygooglephotos

import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import balti.xposed.pixelifygooglephotos.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_MANUAL

/**
 * Applies the spoofed Pixel device/build properties to the `android.os.Build` static fields of
 * the hooked process.
 *
 * Migrated from the legacy `IXposedHookLoadPackage` implementation:
 * - Preferences now come from the framework Remote Preferences (injected by [PixelifyModule])
 *   instead of `XSharedPreferences`.
 * - Legacy `XposedHelpers.setStaticObjectField` is replaced by plain reflection. Each field is
 *   set independently: a failure (e.g. immutable `SDK_INT`, field absent on this Android
 *   version) is logged and skipped instead of aborting the whole spoofing like before.
 */
class DeviceSpoofer(prefs: SharedPreferences?) {

    private val verboseLog: Boolean = prefs?.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false) ?: false

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    /**
     * This will always be null if the user has not chosen to spoof android version.
     */
    private val androidVersionToSpoof: DeviceProps.AndroidVersion? = run {
        if (prefs?.getBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, false) == true)
            finalDeviceToSpoof?.androidVersion
        else {
            prefs?.getString(PREF_SPOOF_ANDROID_VERSION_MANUAL, null)?.let {
                DeviceProps.getAndroidVersionFromLabel(it)
            }
        }
    }

    /**
     * This is the final device to spoof. Defaults to [DeviceProps.defaultDeviceName].
     */
    private val finalDeviceToSpoof: DeviceProps.DeviceEntries? = run {
        val deviceName = prefs?.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
            ?: DeviceProps.defaultDeviceName
        log("Device spoof: $deviceName")
        DeviceProps.getDeviceProps(deviceName)
    }

    /**
     * Rewrite the Build / Build.VERSION static fields of this process, like the original module
     * did through XposedHelpers. Called once per process, right after the hooks are installed.
     */
    fun applyBuildProps() {
        finalDeviceToSpoof?.props?.let { props ->
            if (props.isEmpty()) return@let
            props.forEach { (key, value) -> trySetStaticField(Build::class.java, key, value) }
        }

        androidVersionToSpoof?.getAsMap()?.forEach { (key, value) ->
            trySetStaticField(Build.VERSION::class.java, key, value)
        }
    }

    private fun trySetStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
            if (verboseLog) log("DEVICE PROPS: ${clazz.simpleName}.$fieldName - $value")
        } catch (t: Throwable) {
            log("Failed to spoof ${clazz.name}.$fieldName = $value: $t")
        }
    }

    private companion object {
        const val TAG = "PixelifyGooglePhotos"
    }
}
