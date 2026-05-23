#include <libultraship/bridge.h>
#include "GameInteractor/GameInteractor.h"

void RegisterInfiniteEponaCarrots() {
    REGISTER_VB_SHOULD(VB_CONSUME_EPONA_CARROT, {
        if (CVarGetInteger("gCheats.InfiniteEponaCarrots", 0)) {
            *should = false;
        }
    });
}
