package com.terminads.mm

/**
 * The only place in the Kotlin layer permitted to call into native code.
 *
 * Declared as a plain object without @JvmStatic, so the JNI entry point takes
 * (JNIEnv*, jobject) and resolves as
 * Java_com_terminads_mm_NativeBridge_nativeGetUptimeMillis.
 *
 * The native library is loaded by SDLActivity during MainActivity.onCreate.
 * Calls made before that throw UnsatisfiedLinkError, which we translate to a
 * sentinel rather than crashing the second screen.
 */
object NativeBridge {

    private external fun nativeGetUptimeMillis(): Long

    /** Native process uptime in milliseconds, or -1 if native is not loaded yet. */
    fun uptimeMillis(): Long =
        try {
            nativeGetUptimeMillis()
        } catch (e: UnsatisfiedLinkError) {
            -1L
        }
}
