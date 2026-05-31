#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

extern "C" {
#include "functions.h"
#include "variables.h"
}

#define CVAR CVarGetInteger("gEnhancements.Masks.FierceDeitysAnywhere", 0)

void RegisterFierceDeityAnywhere() {
    REGISTER_VB_SHOULD(VB_DISABLE_FD_MASK, {
        if (CVAR) {
            *should = false;
        }
    });

    REGISTER_VB_SHOULD(VB_USE_ITEM_CONSIDER_ITEM_ACTION, {
        PlayerItemAction itemAction = *va_arg(args, PlayerItemAction*);
        if (CVAR && itemAction == PLAYER_IA_MASK_FIERCE_DEITY) {
            *should = true;
        }
    });

    REGISTER_VB_SHOULD(VB_DISABLE_ITEM_UNDERWATER, {
        s32 item = va_arg(args, s32);
        if (CVAR && item == ITEM_MASK_FIERCE_DEITY &&
            Player_GetEnvironmentalHazard(gPlayState) > PLAYER_ENV_HAZARD_UNDERWATER_FLOOR) {
            *should = false;
        }
    });
}
