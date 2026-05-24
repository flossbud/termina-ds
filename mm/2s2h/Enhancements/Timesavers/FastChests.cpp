#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

void RegisterFastChests() {
    REGISTER_VB_SHOULD(VB_PLAY_SLOW_CHEST_CS, {
        if (CVarGetInteger("gEnhancements.Timesavers.FastChests", 0)) {
            *should = false;
        }
    });
}
