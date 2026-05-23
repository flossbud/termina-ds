#include <libultraship/libultraship.h>
#include "2s2h/GameInteractor/GameInteractor.h"

extern "C" {
#include "variables.h"
#include "overlays/kaleido_scope/ovl_kaleido_scope/z_kaleido_scope.h"
}

static int frameAdvanceTimer = 0;

void RegisterEasyFrameAdvance() {
    GameInteractor::Instance->RegisterGameHook<GameInteractor::OnGameStateMainStart>([]() {
        if (!CVarGetInteger("gCheats.EasyFrameAdvance", 0) || gPlayState == NULL) {
            return;
        }

        Input* input = CONTROLLER1(&gPlayState->state);
        PauseContext* pauseCtx = &gPlayState->pauseCtx;

        if (frameAdvanceTimer > 0 && pauseCtx->state == PAUSE_STATE_OFF) {
            frameAdvanceTimer--;
            if (frameAdvanceTimer == 0 && CHECK_BTN_ALL(input->cur.button, BTN_START)) {
                input->press.button |= BTN_START;
            }
        }

        if (pauseCtx->state == PAUSE_STATE_UNPAUSE_CLOSE) {
            frameAdvanceTimer = 2;
        }
    });
}
