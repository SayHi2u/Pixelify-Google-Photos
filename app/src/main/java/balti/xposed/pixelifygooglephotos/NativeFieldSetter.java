package balti.xposed.pixelifygooglephotos;

import android.util.Log;

import java.lang.reflect.Field;

/**
 * Native helper that clears the ART {@code ArtField} {@code ACC_FINAL} flag for a
 * {@link Field} so that a subsequent reflective write succeeds on Android 17+.
 *
 * <p>Android 17 rejects reflective writes to {@code static final} fields for
 * apps targeting API 37+, even after {@link Field#setAccessible(boolean)}. The
 * check reads {@code ArtField::access_flags_} in the target process, so clearing
 * the reflective {@code Field.accessFlags} copy is not enough. This helper
 * clears the real flag inside the current process only.</p>
 *
 * <p>The helper is deliberately conservative: it validates that the ART access
 * flags agree with {@link Field#getModifiers()} before touching anything. If the
 * ART layout does not match (for example, when ART uses index-based field IDs),
 * it returns {@code false} and the caller keeps the original failure.</p>
 */
public final class NativeFieldSetter {

    private static final String TAG = "PixelifyGooglePhotos";

    private static final boolean LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("pixelify");
            loaded = true;
        } catch (Throwable t) {
            Log.e(TAG, "Native field setter could not be loaded", t);
            loaded = false;
        }
        LOADED = loaded;
    }

    private NativeFieldSetter() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * Clears {@code ACC_FINAL} on the ART field backing {@code field}.
     *
     * @param field     the reflective field to make writable
     * @param modifiers {@code field.getModifiers()}, used to validate the ART layout
     * @return true when the ART field is no longer final and a reflective write can be retried
     */
    public static native boolean makeFieldWritable(Field field, int modifiers);
}
