#include <jni.h>
#include <android/log.h>
#include <cstdint>

namespace {

constexpr const char *kLogTag = "PixelifyGooglePhotos";

// Java access flags as reported by java.lang.reflect.Field#getModifiers().
constexpr uint32_t kAccJavaFlagsMask = 0xFFFFu;
constexpr uint32_t kAccStatic = 0x0008u;
constexpr uint32_t kAccFinal = 0x0010u;

}  // namespace

/**
 * Clears ACC_FINAL on the ART ArtField backing the given reflective Field.
 *
 * On Android 17, java.lang.reflect.Field#set() calls
 * ThrowIAEIfFieldIsNotOverwritable(), which reads ArtField::access_flags_ for
 * the target process. Clearing the reflective Field.accessFlags copy does not
 * affect that check. This function edits the actual ArtField in the current
 * process, so the following Field.set() can write the value.
 *
 * This is the same ART metadata edit that Vector's Android 17 XposedHelpers
 * workaround performs (JingMatrix/Vector commit 4455239, #821).
 *
 * ArtField starts with a 4-byte GcRoot<Class> followed by uint32_t
 * access_flags_. The access flags are validated against Field.getModifiers()
 * before anything is written; if the layout is different (for example, ART
 * index-based field IDs), the function returns false instead of corrupting
 * memory.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_balti_xposed_pixelifygooglephotos_NativeFieldSetter_makeFieldWritable(
        JNIEnv *env, jclass, jobject field, jint modifiers) {
    if (field == nullptr) {
        return JNI_FALSE;
    }

    auto *art_field = reinterpret_cast<uint32_t *>(env->FromReflectedField(field));
    if (art_field == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "makeFieldWritable: FromReflectedField returned null");
        return JNI_FALSE;
    }

    const uint32_t flags = art_field[1];
    if ((flags & kAccJavaFlagsMask) != static_cast<uint32_t>(modifiers)) {
        __android_log_print(
                ANDROID_LOG_ERROR, kLogTag,
                "makeFieldWritable: ArtField layout mismatch (flags=0x%x, modifiers=0x%x)",
                flags, static_cast<uint32_t>(modifiers));
        return JNI_FALSE;
    }
    if ((flags & kAccStatic) == 0) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "makeFieldWritable: refusing non-static field");
        return JNI_FALSE;
    }

    art_field[1] = flags & ~kAccFinal;
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
                        "makeFieldWritable: cleared ACC_FINAL (modifiers=0x%x)",
                        static_cast<uint32_t>(modifiers));
    return JNI_TRUE;
}
