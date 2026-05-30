#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"
#include "2s2h/ShipInit.hpp"

extern "C" {
#include "functions.h"
#include "variables.h"
}

#define CVAR_NAME "gEnhancements.Cutscenes.SkipMiscInteractions"
#define CVAR CVarGetInteger(CVAR_NAME, 0)

static void RegisterSkipHungryGoronDialogue() {
    COND_ID_HOOK(ShouldActorInit, ACTOR_EN_GEG, CVAR, [](Actor*, bool*) {
        if (!CHECK_WEEKEVENTREG(WEEKEVENTREG_35_40)) {
            SET_WEEKEVENTREG(WEEKEVENTREG_35_40);
        }
    });
}

static RegisterShipInitFunc initFunc(RegisterSkipHungryGoronDialogue, { CVAR_NAME });
