#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

extern "C" {
#include "variables.h"
#include "src/overlays/actors/ovl_En_Bom_Chu/z_en_bom_chu.h"

void EnBomChu_Move(EnBomChu*, PlayState*);
void EnBomChu_Explode(EnBomChu*, PlayState*);
void EnBomChu_UpdateRotation(EnBomChu*);
}

#define CVAR_NAME "gEnhancements.PlayerActions.RemoteBombchu"
#define CVAR CVarGetInteger(CVAR_NAME, 0)

static bool sFocused = false;
static EnBomChu* sActiveBombchu = nullptr;

bool IsBombchuFocused() {
    return sFocused;
}

static void ReleaseBombchuFocus() {
    Player* player = GET_PLAYER(gPlayState);

    Camera_SetFocalActor(Play_GetCamera(gPlayState, player->subCamId), &player->actor);
    player->stateFlags1 &= ~PLAYER_STATE1_20;
    sFocused = false;
}

void RegisterRemoteBombchu() {
    COND_ID_HOOK(OnActorInit, ACTOR_EN_BOM_CHU, CVAR, [](Actor* actor) { sActiveBombchu = (EnBomChu*)actor; });

    COND_ID_HOOK(OnActorDestroy, ACTOR_EN_BOM_CHU, CVAR, [](Actor* actor) {
        if (actor == (Actor*)sActiveBombchu) {
            sActiveBombchu = nullptr;

            if (sFocused) {
                ReleaseBombchuFocus();
            }
        }
    });

    COND_ID_HOOK(OnActorUpdate, ACTOR_EN_BOM_CHU, CVAR, [](Actor* actor) {
        if (actor != (Actor*)sActiveBombchu) {
            return;
        }

        Player* player = GET_PLAYER(gPlayState);
        Input* input = &gPlayState->state.input[0];

        if (sActiveBombchu->actionFunc == EnBomChu_Move) {
            if (!sFocused) {
                Camera_SetFocalActor(Play_GetCamera(gPlayState, CutsceneManager_GetCurrentSubCamId(actor->csId)), actor);
                player->stateFlags1 |= PLAYER_STATE1_20;
                sFocused = true;
            }

            f32 stickX = input->cur.stick_x;
            if (fabsf(stickX) > 10.0f) {
                s16 turnAngle = (s16)(1500.0f * (stickX / 85.0f));

                Matrix_RotateAxisF(BINANG_TO_RAD(-turnAngle), &sActiveBombchu->axisUp, MTXMODE_NEW);

                Vec3f newAxisForwards;
                Matrix_MultVec3f(&sActiveBombchu->axisForwards, &newAxisForwards);
                Math_Vec3f_Copy(&sActiveBombchu->axisForwards, &newAxisForwards);
                Math3D_Vec3f_Cross(&sActiveBombchu->axisUp, &sActiveBombchu->axisForwards,
                                    &sActiveBombchu->axisLeft);

                EnBomChu_UpdateRotation(sActiveBombchu);
                sActiveBombchu->actor.shape.rot.x = -sActiveBombchu->actor.world.rot.x;
                sActiveBombchu->actor.shape.rot.y = sActiveBombchu->actor.world.rot.y;
                sActiveBombchu->actor.shape.rot.z = sActiveBombchu->actor.world.rot.z;
            }

            if (input->press.button & BTN_B) {
                EnBomChu_Explode(sActiveBombchu, gPlayState);
            }

            if (input->press.button & BTN_A) {
                sActiveBombchu = nullptr;
                ReleaseBombchuFocus();
            }
        }
    });
}
