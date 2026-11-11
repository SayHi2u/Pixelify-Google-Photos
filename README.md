# Pixelify-Google-Photos

⚠️⚠️ This project is EOL. I have entirely stopped using Google Photos. Sorry for the inconvenience. ⚠️⚠️

LSPosed / EdXposed module to add Google Pixel features on Google Photos for any device.  
Tested on EdXposed by [Jim Wu](https://github.com/MlgmXyysd)  

[LSPosed module repo](https://github.com/Xposed-Modules-Repo/balti.xposed.pixelifygooglephotos.git)  
[Development repo](https://github.com/BaltiApps/Pixelify-Google-Photos.git)  

[Telegram group link](https://t.me/pixelifyGooglePhotos)  

### Steps to use:
1. Install Magisk, [LSPosed](https://github.com/LSPosed/LSPosed) Or [EdXposed](https://github.com/ElderDrivers/EdXposed).  
2. Install the apk of this app (available from [Releases](https://github.com/BaltiApps/Pixelify-Google-Photos/releases) page.)  
3. Open LSPosed / EdXposed app and enable the module. For LSPosed, Google Photos will be automatically selected.  
4. Reboot. Enjoy. (If needed, you might need to clear data of Google Photos app).  

### How does this module work?
It simply hooks on to `hasSystemFeature()` method under `android.app.ApplicationPackageManager` class. 
Then when Google Photos checks the relevant features which are expected only on Pixel devices, the module passes `true`. 
Thus Google Photos enables Pixel-Exclusive features.  
The features being "spoofed" can be found from:  
[Dot OS sources](https://github.com/DotOS/android_vendor_dot/blob/55f1c26bb6dbb1175d96cf538ae113618caf7d06/prebuilt/common/etc/pixel_2016_exclusive.xml)  
[PixelFeatureDrops magisk module](https://github.com/ayush5harma/PixelFeatureDrops/tree/master/system/etc/sysconfig)  
This module can also spoof some of the `build.prop` information like `BRAND`, `MANUFACTURER`, `MODEL`, `FINGERPRINT` of some Pixel devices.  

### Building with the Android 17 native helper

The module now contains a small JNI helper (`app/src/main/cpp/field_setter.cpp`) that clears the
ART `ArtField` `ACC_FINAL` flag inside the hooked Google Photos process. It is required for
`Build.MODEL` and the other `static final` Build fields on Android 17 / targetSdk 37.

Requirements:

- Android NDK **27.2.12479018** (set via `ndkVersion` in `app/build.gradle`)
- CMake **3.22.1**
- For a release APK, create an untracked `keystore.properties` in the repository root:

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

If `keystore.properties` is absent, `assembleRelease` produces an unsigned APK instead of
silently signing it with the public debug key. `assembleDebug` still works for local testing.

```bash
./gradlew :app:assembleDebug
# or, after configuring keystore.properties:
./gradlew :app:assembleRelease
```

### Disclaimer!!
The user takes sole responsibility for any damage that might arise due to use of this module.  
This includes physical damage (to device), injury, data loss, and also legal matters.  
This project was made as a learning initiative and the developer cannot be held liable in any way for the use of it.
