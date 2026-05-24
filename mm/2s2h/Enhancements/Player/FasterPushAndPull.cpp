#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

extern "C" {
#include "macros.h"
#include "overlays/actors/ovl_Bg_Dblue_Movebg/z_bg_dblue_movebg.h"
#include "overlays/actors/ovl_Bg_Ikana_Block/z_bg_ikana_block.h"
#include "overlays/actors/ovl_Obj_Oshihiki/z_obj_oshihiki.h"
#include "overlays/actors/ovl_Obj_Skateblock/z_obj_skateblock.h"
}

void RegisterFasterPushAndPull() {
    REGISTER_VB_SHOULD(VB_GREAT_BAY_GEAR_CLAMP_PUSH_SPEED, {
        if (!CVarGetInteger("gEnhancements.Player.FasterPushAndPull", 0)) {
            return;
        }

        BgDblueMovebg* bgDblueMovebg = va_arg(args, BgDblueMovebg*);
        *should = false;
        bgDblueMovebg->unk_188 = 20;
    });

    REGISTER_VB_SHOULD(VB_PUSH_BLOCK_SET_SPEED, {
        if (!CVarGetInteger("gEnhancements.Player.FasterPushAndPull", 0)) {
            return;
        }

        ObjOshihiki* objOshihiki = va_arg(args, ObjOshihiki*);
        objOshihiki->pushSpeed = 5.0f;
        *should = false;
    });

    REGISTER_VB_SHOULD(VB_PUSH_BLOCK_SET_TIMER, {
        if (!CVarGetInteger("gEnhancements.Player.FasterPushAndPull", 0)) {
            return;
        }

        Actor* actor = va_arg(args, Actor*);
        if (actor->id == ACTOR_OBJ_OSHIHIKI) {
            ((ObjOshihiki*)actor)->timer = 2;
        } else if (actor->id == ACTOR_BG_IKANA_BLOCK) {
            ((BgIkanaBlock*)actor)->unk_17B = 11;
        }

        *should = false;
    });

    REGISTER_VB_SHOULD(VB_SKATE_BLOCK_BEGIN_MOVE, {
        if (!CVarGetInteger("gEnhancements.Player.FasterPushAndPull", 0)) {
            return;
        }

        ObjSkateblock* objSkateblock = va_arg(args, ObjSkateblock*);
        s32 directionIndex = va_arg(args, s32);
        *should = objSkateblock->unk_172[directionIndex] > 0;
    });

    REGISTER_VB_SHOULD(VB_BLOCK_BEGIN_MOVE, {
        if (CVarGetInteger("gEnhancements.Player.FasterPushAndPull", 0)) {
            *should = true;
        }
    });

    REGISTER_VB_SHOULD(VB_BLOCK_BE_FINISHED_PULLING, {
        if (!CVarGetInteger("gEnhancements.Player.FasterPushAndPull", 0)) {
            return;
        }

        f32* value = va_arg(args, f32*);
        f32 target = (f32)va_arg(args, f64);
        f32 step = (f32)va_arg(args, f64);
        f32 maxStep = (f32)va_arg(args, f64);

        step = CLAMP_MAX(step, maxStep);
        *should = Math_StepToF(value, target, step);
    });
}
