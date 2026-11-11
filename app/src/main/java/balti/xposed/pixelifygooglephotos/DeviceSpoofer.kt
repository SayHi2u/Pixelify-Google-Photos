package balti.xposed.pixelifygooglephotos

import android.content.SharedPreferences
import android.os.Build
import balti.xposed.pixelifygooglephotos.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_MANUAL
import java.lang.reflect.Field

/**
 * Applies the spoofed Pixel device/build properties to the `android.os.Build` static fields of
 * the hooked process.
 *
 * Android 17 rejects reflective writes to `static final` fields for apps targeting API 37+.
 * A plain [Field.set] therefore throws `IllegalAccessException` for every `android.os.Build`
 * field. When that happens this class asks [NativeFieldSetter] to clear the ART `ACC_FINAL`
 * flag for the exact field and retries the write. The native helper only touches the current
 * process; no system property, system_server or other app is modified.
 *
 * Every write is read back and verified. A failure is logged with [ModuleLog.e] instead of
 * being swallowed, and [applyBuildProps] returns a result that the module entry point reports.
 */
class DeviceSpoofer(prefs: SharedPreferences?) {

    private val verboseLog: Boolean = prefs?.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false) ?: false

    /**
     * This is the final device to spoof. Defaults to [DeviceProps.defaultDeviceName].
     *
     * Declared before [androidVersionToSpoof] on purpose: Kotlin initializes properties in
     * declaration order, and the version initializer reads this property.
     */
    private val finalDeviceToSpoof: DeviceProps.DeviceEntries? = run {
        val configured = prefs?.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
        val deviceName = configured?.takeIf { it.isNotBlank() } ?: DeviceProps.defaultDeviceName
        if (deviceName != configured) {
            ModuleLog.w("Invalid/blank device preference; falling back to $deviceName")
        }
        ModuleLog.i("Device spoof: $deviceName")
        DeviceProps.getDeviceProps(deviceName).also {
            if (it == null) ModuleLog.e("Unknown device to spoof: $deviceName")
        }
    }

    /**
     * This will always be null if the user has not chosen to spoof android version.
     */
    private val androidVersionToSpoof: DeviceProps.AndroidVersion? = run {
        if (prefs?.getBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, false) == true) {
            finalDeviceToSpoof?.androidVersion
        } else {
            prefs?.getString(PREF_SPOOF_ANDROID_VERSION_MANUAL, null)?.let {
                DeviceProps.getAndroidVersionFromLabel(it)
            }
        }
    }

    data class BuildPropsResult(
        val attempted: Int,
        val applied: Int,
        val failed: Int,
        val versionAttempted: Int,
        val versionApplied: Int,
        val versionSkipped: Boolean,
    ) {
        val hasFailures: Boolean get() = failed > 0 || versionApplied < versionAttempted
    }

    /**
     * Rewrite the Build / Build.VERSION static fields of this process.
     */
    fun applyBuildProps(): BuildPropsResult {
        var attempted = 0
        var applied = 0
        var failed = 0

        val device = finalDeviceToSpoof
        if (device == null) {
            ModuleLog.e("No valid device to spoof; Build props were not applied")
            return BuildPropsResult(0, 0, 0, 0, 0, false)
        }

        device.props.forEach { (key, value) ->
            attempted++
            if (trySetStaticField(Build::class.java, key, value)) applied++ else failed++
        }

        var versionAttempted = 0
        var versionApplied = 0
        var versionSkipped = false
        val version = androidVersionToSpoof
        if (version != null) {
            val realSdk = Build.VERSION.SDK_INT
            when {
                version.sdk < realSdk -> {
                    // Keeping SDK_INT below the real level makes apps take legacy code paths
                    // while the framework still enforces current rules. That is a known source
                    // of startup crashes, so skip the whole version spoof instead.
                    versionSkipped = true
                    ModuleLog.w(
                        "Skipping Build.VERSION spoof: target SDK ${version.sdk} < real $realSdk"
                    )
                }
                else -> {
                    if (version.sdk > realSdk) {
                        ModuleLog.w(
                            "Spoofing Build.VERSION.SDK_INT ${version.sdk} > real $realSdk; " +
                                "this can make apps call APIs that do not exist"
                        )
                    }
                    version.getAsMap().forEach { (key, value) ->
                        versionAttempted++
                        if (trySetStaticField(Build.VERSION::class.java, key, value)) {
                            versionApplied++
                        }
                    }
                }
            }
        }

        ModuleLog.i(
            "Build props: applied=$applied/$attempted, " +
                "version=$versionApplied/$versionAttempted, versionSkipped=$versionSkipped"
        )
        if (failed > 0 || versionApplied < versionAttempted) {
            ModuleLog.e(
                "Some Build props could not be spoofed " +
                    "(deviceFailed=$failed, versionFailed=${versionAttempted - versionApplied})"
            )
        }

        return BuildPropsResult(
            attempted = attempted,
            applied = applied,
            failed = failed,
            versionAttempted = versionAttempted,
            versionApplied = versionApplied,
            versionSkipped = versionSkipped,
        )
    }

    private fun trySetStaticField(clazz: Class<*>, fieldName: String, value: Any?): Boolean {
        val field = try {
            clazz.getDeclaredField(fieldName)
        } catch (t: Throwable) {
            ModuleLog.e("Field ${clazz.name}.$fieldName not found", t)
            return false
        }

        try {
            field.isAccessible = true
        } catch (t: Throwable) {
            ModuleLog.w("Could not make ${clazz.name}.$fieldName accessible", t)
        }

        return try {
            field.set(null, value)
            verify(field, value, clazz, fieldName)
        } catch (t: Throwable) {
            if (!tryNativeRewrite(field, value, clazz, fieldName, t)) {
                false
            } else {
                verify(field, value, clazz, fieldName)
            }
        }
    }

    /**
     * Android 17 path: clear the ART ArtField ACC_FINAL flag, then retry the reflective write.
     */
    private fun tryNativeRewrite(
        field: Field,
        value: Any?,
        clazz: Class<*>,
        fieldName: String,
        original: Throwable,
    ): Boolean {
        if (!NativeFieldSetter.isLoaded()) {
            ModuleLog.e(
                "Cannot spoof ${clazz.name}.$fieldName: ${original.message}; " +
                    "native helper is not loaded",
                original,
            )
            return false
        }

        val cleared = try {
            NativeFieldSetter.makeFieldWritable(field, field.modifiers)
        } catch (t: Throwable) {
            ModuleLog.e("Native makeFieldWritable failed for ${clazz.name}.$fieldName", t)
            false
        }
        if (!cleared) {
            ModuleLog.e(
                "Cannot spoof ${clazz.name}.$fieldName: ${original.message}; " +
                    "could not clear ACC_FINAL",
                original,
            )
            return false
        }

        return try {
            field.set(null, value)
            if (verboseLog) {
                ModuleLog.d("Native ACC_FINAL clear + set: ${clazz.simpleName}.$fieldName")
            }
            true
        } catch (t: Throwable) {
            ModuleLog.e(
                "Reflective write still failed after clearing ACC_FINAL for " +
                    "${clazz.name}.$fieldName",
                t,
            )
            false
        }
    }

    private fun verify(field: Field, expected: Any?, clazz: Class<*>, fieldName: String): Boolean {
        return try {
            val actual = field.get(null)
            if (actual == expected) {
                if (verboseLog) {
                    ModuleLog.d("DEVICE PROPS: ${clazz.simpleName}.$fieldName = $actual")
                }
                true
            } else {
                ModuleLog.e(
                    "Verification failed for ${clazz.name}.$fieldName: " +
                        "expected=$expected actual=$actual"
                )
                false
            }
        } catch (t: Throwable) {
            ModuleLog.e("Verification read failed for ${clazz.name}.$fieldName", t)
            false
        }
    }
}
