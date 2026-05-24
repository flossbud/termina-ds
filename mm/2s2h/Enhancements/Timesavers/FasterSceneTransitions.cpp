#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

extern "C" {
#include "variables.h"
#include "z64.h"
#include "z64transition.h"

void Play_SetupTransition(PlayState* playState, s32 transitionType);
}

static void SetupFasterSceneTransition() {
    auto transitionType = gPlayState->transitionType;

    switch (transitionType) {
        case TRANS_TYPE_FADE_BLACK:
            transitionType = TRANS_TYPE_FADE_BLACK_FAST;
            break;
        case TRANS_TYPE_FADE_WHITE:
            transitionType = TRANS_TYPE_FADE_WHITE_FAST;
            break;
        case TRANS_TYPE_WIPE:
            transitionType = TRANS_TYPE_WIPE_FAST;
            break;
        case TRANS_TYPE_FILL_WHITE:
            transitionType = TRANS_TYPE_FILL_WHITE_FAST;
            break;
        case TRANS_TYPE_FADE_DYNAMIC:
            transitionType = gSaveContext.save.isNight ? TRANS_TYPE_FADE_BLACK_FAST : TRANS_TYPE_FADE_WHITE_FAST;
            break;
    }

    gPlayState->transitionType = transitionType;
}

void RegisterFasterSceneTransitions() {
    REGISTER_VB_SHOULD(VB_SETUP_TRANSITION, {
        if (!CVarGetInteger("gEnhancements.Timesavers.FasterSceneTransitions", 0)) {
            return;
        }

        switch (gPlayState->sceneId) {
            case SCENE_SPOT00:
            case SCENE_KAKUSIANA:
            case SCENE_HAKASHITA:
                break;
            default:
                SetupFasterSceneTransition();
                Play_SetupTransition(gPlayState, gPlayState->transitionType);
                *should = false;
                break;
        }
    });
}
