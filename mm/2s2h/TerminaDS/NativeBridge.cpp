/*
 * Termina DS: JNI seam between the Compose second screen and the game core.
 *
 * Phase 1 exposes native uptime only. That proves the bridge is wired end to
 * end; it does not prove the game loop is running. Real game state arrives in
 * Phase 2.
 *
 * This file lives under mm/2s2h/, which mm/CMakeLists.txt globs recursively,
 * so it compiles with no CMake change.
 */
#ifdef __ANDROID__

#include <jni.h>
#include <chrono>
#include <cstdint>

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

#endif // __ANDROID__
