package balti.xposed.pixelifygooglephotos

object Constants {

    val PACKAGE_NAME_GOOGLE_PHOTOS = "com.google.android.apps.photos"

    val TELEGRAM_GROUP = "https://t.me/pixelifyGooglePhotos"

    // Point the updater at this fork, not the abandoned upstream repository.
    val UPDATE_INFO_URL =
        "https://raw.githubusercontent.com/SayHi2u/Pixelify-Google-Photos/api102-migration/update_info.json"
    val UPDATE_INFO_URL2 =
        "https://raw.githubusercontent.com/SayHi2u/Pixelify-Google-Photos/main/update_info.json"
    val RELEASES_URL = "https://github.com/SayHi2u/Pixelify-Google-Photos/releases"
    val RELEASES_URL2 = "https://github.com/SayHi2u/Pixelify-Google-Photos/releases"

    val FIELD_LATEST_VERSION_CODE = "latest_version_code"

    /**
     * Group name of the framework "Remote Preferences" shared by the module UI process and
     * the hooked Google Photos process. Replaces the legacy MODE_WORLD_READABLE
     * SharedPreferences + XSharedPreferences mechanism, which is no longer supported by
     * the Modern Xposed API (101/102).
     */
    const val PREFS_GROUP = "pixelify"

    val CONF_EXPORT_NAME = "pgp_conf.json"

    val PREF_SPOOF_FEATURES_LIST = "PREF_SPOOF_FEATURES_LIST"
    val PREF_DEVICE_TO_SPOOF = "PREF_DEVICE_TO_SPOOF"
    val PREF_STRICTLY_CHECK_GOOGLE_PHOTOS = "PREF_STRICTLY_CHECK_GOOGLE_PHOTOS"
    val PREF_OVERRIDE_ROM_FEATURE_LEVELS = "PREF_OVERRIDE_ROM_FEATURE_LEVELS"
    val PREF_ENABLE_VERBOSE_LOGS = "PREF_ENABLE_VERBOSE_LOGS"
    val PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE = "PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE"
    val PREF_SPOOF_ANDROID_VERSION_MANUAL = "PREF_SPOOF_ANDROID_VERSION_MANUAL"
    val PREF_LAST_VERSION = "PREF_LAST_VERSION"
}
