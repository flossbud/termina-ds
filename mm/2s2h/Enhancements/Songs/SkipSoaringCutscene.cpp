#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"
#include "2s2h/ShipInit.hpp"

extern "C" {
#include "variables.h"
#include "overlays/actors/ovl_En_Test7/z_en_test7.h"

extern u16 D_80AF343C[];
}

#define CVAR_NAME "gEnhancements.Songs.SkipSoaringCutscene"
#define CVAR CVarGetInteger(CVAR_NAME, 0)

static void SkipSoaringCutscene(Actor* actor, bool* should) {
    s16 ocarinaMode = ENTEST7_GET(actor);

    if (ocarinaMode == ENTEST7_MINUS1) {
        return;
    }

    *should = false;

    if (gPlayState->sceneId == SCENE_SECOM) {
        gPlayState->nextEntrance = ENTRANCE(IKANA_CANYON, 6);
    } else if (ocarinaMode == ENTEST7_26) {
        func_80169F78(&gPlayState->state);
        gSaveContext.respawn[RESPAWN_MODE_TOP].playerParams =
            (gSaveContext.respawn[RESPAWN_MODE_TOP].playerParams & 0xFF) | 0x600;
        gSaveContext.respawnFlag = -6;
    } else {
        gPlayState->nextEntrance = D_80AF343C[ocarinaMode - ENTEST7_1C];
        if ((gPlayState->nextEntrance == ENTRANCE(SOUTHERN_SWAMP_POISONED, 10)) &&
            CHECK_WEEKEVENTREG(WEEKEVENTREG_CLEARED_WOODFALL_TEMPLE)) {
            gPlayState->nextEntrance = ENTRANCE(SOUTHERN_SWAMP_CLEARED, 10);
        } else if ((gPlayState->nextEntrance == ENTRANCE(MOUNTAIN_VILLAGE_WINTER, 8)) &&
                   CHECK_WEEKEVENTREG(WEEKEVENTREG_CLEARED_SNOWHEAD_TEMPLE)) {
            gPlayState->nextEntrance = ENTRANCE(MOUNTAIN_VILLAGE_SPRING, 8);
        }
    }

    gPlayState->transitionTrigger = TRANS_TRIGGER_START;
    gPlayState->transitionType = TRANS_TYPE_FADE_BLACK;
    gSaveContext.seqId = (u8)NA_BGM_DISABLED;
    gSaveContext.ambienceId = AMBIENCE_ID_DISABLED;
}

static void RegisterSkipSoaringCutscene() {
    COND_ID_HOOK(ShouldActorInit, ACTOR_EN_TEST7, CVAR, SkipSoaringCutscene);
}

static RegisterShipInitFunc initFunc(RegisterSkipSoaringCutscene, { CVAR_NAME });
