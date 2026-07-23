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
 * TDS_SNAP_COUNT elements. Returns JNI_FALSE if it is too short or if the
 * seqlock retry budget was exhausted; the caller keeps its previous snapshot.
 *
 * Allocates nothing: SetIntArrayRegion writes into the caller's reusable array,
 * so there are no local references to leak at 10 Hz.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_terminads_mm_NativeBridge_nativeReadSnapshot(JNIEnv* env, jobject thiz, jintArray out) {
    (void)thiz;

    if (out == nullptr) {
        return JNI_FALSE;
    }
    if (env->GetArrayLength(out) < TDS_SNAP_COUNT) {
        return JNI_FALSE;
    }

    int32_t values[TDS_SNAP_COUNT];
    if (!TerminaDS_ReadSnapshot(values, TDS_SNAP_COUNT)) {
        return JNI_FALSE;
    }

    env->SetIntArrayRegion(out, 0, TDS_SNAP_COUNT, reinterpret_cast<const jint*>(values));
    return JNI_TRUE;
}

#endif // __ANDROID__
