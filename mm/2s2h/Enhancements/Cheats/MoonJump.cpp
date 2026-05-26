#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"
#include "2s2h/ShipInit.hpp"
#include "variables.h"

#define CVAR_NAME "gCheats.MoonJumpOnL"

static HOOK_ID moonJumpOnLGameStateUpdateHookId = 0;
void RegisterMoonJumpOnL() {
    if (moonJumpOnLGameStateUpdateHookId) {
        GameInteractor::Instance->UnregisterGameHook<GameInteractor::OnGameStateUpdate>(
            moonJumpOnLGameStateUpdateHookId);
        moonJumpOnLGameStateUpdateHookId = 0;
    }

    if (CVarGetInteger(CVAR_NAME, 0)) {
        moonJumpOnLGameStateUpdateHookId =
            GameInteractor::Instance->RegisterGameHook<GameInteractor::OnGameStateUpdate>([]() {
                if (gPlayState == nullptr)
                    return;

                Player* player = GET_PLAYER(gPlayState);

                if (player != nullptr && CHECK_BTN_ANY(gPlayState->state.input[0].cur.button, BTN_L)) {
                    player->actor.velocity.y = 6.34375f;
                }
            });
    }
}

static RegisterShipInitFunc initFunc(RegisterMoonJumpOnL, { CVAR_NAME });
