#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

extern "C" {
#include "overlays/actors/ovl_Obj_Syokudai/z_obj_syokudai.h"
}

void RegisterSkipOnePointCutscenes() {
    REGISTER_VB_SHOULD(VB_CAMERA_SET_FOCAL_ACTOR, {
        if (!CVarGetInteger("gEnhancements.Cutscenes.SkipOnePointCutscenes", 0)) {
            return;
        }

        Actor* actor = va_arg(args, Actor*);

        if (actor->id == ACTOR_EN_BAL) {
            *should = false;
        }
    });

    REGISTER_VB_SHOULD(VB_START_CUTSCENE, {
        if (!CVarGetInteger("gEnhancements.Cutscenes.SkipOnePointCutscenes", 0)) {
            return;
        }

        s16* csId = va_arg(args, s16*);
        Actor* actor = va_arg(args, Actor*);

        if (*csId == CS_ID_NONE || actor == NULL) {
            return;
        }

        switch (actor->id) {
            case ACTOR_OBJ_SYOKUDAI: {
                ObjSyokudai* torch = (ObjSyokudai*)actor;
                s32 switchFlag = OBJ_SYOKUDAI_GET_SWITCH_FLAG(actor);

                if (torch->pendingAction >= OBJ_SYOKUDAI_PENDING_ACTION_CUTSCENE_AND_SWITCH) {
                    Flags_SetSwitch(gPlayState, switchFlag);
                }

                torch->pendingAction = OBJ_SYOKUDAI_PENDING_ACTION_NONE;
                torch->snuffTimer = OBJ_SYOKUDAI_SNUFF_NEVER;
                *should = false;
                break;
            }

            case ACTOR_OBJ_COMB:
                if (gPlayState->sceneId != SCENE_PIRATE) {
                    actor->csId = CS_ID_NONE;
                    *should = false;
                }
                break;

            case ACTOR_OBJ_BEAN:
            case ACTOR_OBJ_MAKEKINSUTA:
            case ACTOR_OBJ_SPIDERTENT:
                actor->csId = CS_ID_NONE;
                *should = false;
                break;

            case ACTOR_EN_BOX:
                if (gPlayState->sceneId != SCENE_TAKARAYA) {
                    *should = false;
                }
                break;

            case ACTOR_EN_CHA:
            case ACTOR_BG_SPDWEB:
            case ACTOR_DOOR_SHUTTER:
            case ACTOR_BG_NUMA_HANA:
            case ACTOR_BG_LADDER:
            case ACTOR_OBJ_RAILLIFT:
            case ACTOR_OBJ_SWITCH:
            case ACTOR_OBJ_FIRESHIELD:
            case ACTOR_OBJ_ICE_POLY:
            case ACTOR_BG_DBLUE_MOVEBG:
            case ACTOR_OBJ_HUNSUI:
            case ACTOR_BG_KIN2_BOMBWALL:
            case ACTOR_BG_ASTR_BOMBWALL:
            case ACTOR_BG_KIN2_PICTURE:
            case ACTOR_BG_HAKUGIN_ELVPOLE:
            case ACTOR_BG_HAKUGIN_SWITCH:
            case ACTOR_BG_HAKUGIN_POST:
            case ACTOR_OBJ_Y2SHUTTER:
            case ACTOR_OBJ_LIGHTBLOCK:
            case ACTOR_OBJ_LIGHTSWITCH:
            case ACTOR_BG_IKANA_BOMBWALL:
            case ACTOR_BG_IKANA_BLOCK:
            case ACTOR_BG_HAKUGIN_BOMBWALL:
            case ACTOR_EN_SW:
            case ACTOR_OBJ_CHAN:
            case ACTOR_EN_MM:
            case ACTOR_BG_F40_BLOCK:
            case ACTOR_EN_BAL:
            case ACTOR_BG_TOBIRA01:
            case ACTOR_OBJ_BIGICICLE:
            case ACTOR_OBJ_HAKAISI:
            case ACTOR_BG_HAKA_BOMBWALL:
            case ACTOR_EN_DRAGON:
            case ACTOR_BG_DBLUE_BALANCE:
            case ACTOR_BG_DBLUE_WATERFALL:
            case ACTOR_OBJ_ICEBLOCK:
            case ACTOR_BG_IKNIN_SUSCEIL:
            case ACTOR_BG_IKANA_DHARMA:
            case ACTOR_OBJ_HUGEBOMBIWA:
            case ACTOR_OBJ_WARPSTONE:
                *should = false;
                break;

            default:
                break;
        }
    });
}
