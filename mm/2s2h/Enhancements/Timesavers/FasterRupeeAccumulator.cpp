#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

extern "C" {
#include "variables.h"
}

void RegisterFasterRupeeAccumulator() {
    GameInteractor::Instance->RegisterGameHook<GameInteractor::OnGameStateUpdate>([]() {
        if (!CVarGetInteger("gEnhancements.Timesavers.FasterRupeeAccumulator", 0) || gPlayState == NULL ||
            gSaveContext.rupeeAccumulator == 0) {
            return;
        }

        s16 capacity = (s16)CUR_CAPACITY(UPG_WALLET);
        s16 step = capacity / 50;

        if (gSaveContext.rupeeAccumulator > 0) {
            s16 amount = MIN(step, MIN(gSaveContext.rupeeAccumulator,
                                       (s16)(capacity - gSaveContext.save.saveInfo.playerData.rupees)));
            if (amount > 0) {
                gSaveContext.rupeeAccumulator -= amount;
                gSaveContext.save.saveInfo.playerData.rupees += amount;
            }
        } else {
            s16 amount =
                MIN(step, MIN((s16)(-gSaveContext.rupeeAccumulator), gSaveContext.save.saveInfo.playerData.rupees));
            if (amount > 0) {
                gSaveContext.rupeeAccumulator += amount;
                gSaveContext.save.saveInfo.playerData.rupees -= amount;
            }
        }
    });
}
