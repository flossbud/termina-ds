/*
 * Termina DS: JNI seam between the Compose second screen and the game core.
 *
 * This file is the seam and nothing more -- it holds no engine includes and
 * dereferences no game pointers. Snapshot sampling lives in
 * SnapshotPublisher.cpp; this only marshals an already-consistent copy.
 *
 * This file lives under mm/2s2h/, which mm/CMakeLists.txt globs recursively,
 * so it compiles with no CMake change.
 */
#ifdef __ANDROID__

#include <jni.h>
#include <chrono>
#include <cstdint>

#include "CommandMailbox.h"
#include "GameSnapshot.h"

namespace {
std::chrono::steady_clock::time_point NativeStartTime() {
    static const std::chrono::steady_clock::time_point start = std::chrono::steady_clock::now();
    return start;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_terminads_mm_NativeBridge_nativeGetUptimeMillis(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    const auto elapsed = std::chrono::steady_clock::now() - NativeStartTime();
    const auto millis = std::chrono::duration_cast<std::chrono::milliseconds>(elapsed).count();
    return static_cast<jlong>(millis);
}

/*
 * Copy the latest published snapshot into `out`, which must have at least
 * TDS_SNAP_COUNT elements.
 *
 * Returns a TdsSnapshotStatus (see GameSnapshot.h) rather than a boolean,
 * because the two failures are opposites: TDS_SNAP_STATUS_RETRY_EXHAUSTED is a
 * transient seqlock collision the caller rides out by keeping its previous
 * snapshot, while TDS_SNAP_STATUS_BUFFER_TOO_SMALL means this build's Kotlin
 * mirror is shorter than the native payload and every future call will fail the
 * same way. Collapsed into one `false` they were indistinguishable, and the
 * caller's only sane default -- assume transient -- rendered the permanent case
 * as "publisher has not run".
 *
 * Allocates nothing: SetIntArrayRegion writes into the caller's reusable array,
 * so there are no local references to leak at 10 Hz.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_terminads_mm_NativeBridge_nativeReadSnapshot(JNIEnv* env, jobject thiz, jintArray out) {
    (void)thiz;

    if (out == nullptr) {
        return static_cast<jint>(TDS_SNAP_STATUS_BUFFER_TOO_SMALL);
    }
    if (env->GetArrayLength(out) < TDS_SNAP_COUNT) {
        return static_cast<jint>(TDS_SNAP_STATUS_BUFFER_TOO_SMALL);
    }

    int32_t values[TDS_SNAP_COUNT];
    const int32_t status = TerminaDS_ReadSnapshot(values, TDS_SNAP_COUNT);
    if (status != TDS_SNAP_STATUS_OK) {
        return static_cast<jint>(status);
    }

    env->SetIntArrayRegion(out, 0, TDS_SNAP_COUNT, reinterpret_cast<const jint*>(values));
    return static_cast<jint>(TDS_SNAP_STATUS_OK);
}

/*
 * Submit one absolute command to the game thread. Returns a TdsSubmitStatus.
 * The name string is copied into the ring before this returns; no reference
 * is retained.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_terminads_mm_NativeBridge_nativeSubmitCommand(JNIEnv* env, jobject thiz, jint op, jint a,
                                                       jint b, jstring name) {
    (void)thiz;

    const char* nameChars = nullptr;
    if (name != nullptr) {
        nameChars = env->GetStringUTFChars(name, nullptr);
        if (nameChars == nullptr) {
            return static_cast<jint>(TDS_SUBMIT_INVALID);
        }
    }

    const int32_t status = TerminaDS_SubmitCommand(op, a, b, nameChars);

    if (nameChars != nullptr) {
        env->ReleaseStringUTFChars(name, nameChars);
    }
    return static_cast<jint>(status);
}

#endif // __ANDROID__
