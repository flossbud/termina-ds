#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

void RegisterPreventDiveOverWater() {
    REGISTER_VB_SHOULD(VB_LINK_DIVE_OVER_WATER, {
        if (CVarGetInteger("gEnhancements.Player.PreventDiveOverWater", 0)) {
            *should = false;
        }
    });
}
