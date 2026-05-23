#include <libultraship/bridge.h>
#include "GameInteractor/GameInteractor.h"

void RegisterClimbAnywhere() {
    REGISTER_VB_SHOULD(VB_BE_CLIMBABLE_SURFACE, {
        if (CVarGetInteger("gCheats.ClimbAnywhere", 0)) {
            *should = true;
        }
    });
}
