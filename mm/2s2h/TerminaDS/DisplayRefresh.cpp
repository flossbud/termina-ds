#include "DisplayRefresh.h"

#include <atomic>

namespace {

std::atomic<int32_t> sDisplayRefreshHz{ 0 };

} // namespace

extern "C" void TerminaDS_SetDisplayRefreshHz(int32_t hz) {
    sDisplayRefreshHz.store(hz, std::memory_order_relaxed);
}

extern "C" int32_t TerminaDS_GetDisplayRefreshHz(void) {
    return sDisplayRefreshHz.load(std::memory_order_relaxed);
}
