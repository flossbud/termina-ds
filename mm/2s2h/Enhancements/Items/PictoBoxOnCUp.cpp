#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

extern "C" {
#include "variables.h"
#include "z64player.h"
extern Input* sPlayerControlInput;
s32 func_80123420(Player* player);
s32 func_80831814(Player* player, PlayState* play, PlayerUnkAA5 arg2);
}

#define CVAR_NAME "gEnhancements.Items.PictoBoxOnCUp"
#define CVAR CVarGetInteger(CVAR_NAME, 0)

void RegisterPictoBoxOnCUp() {
    COND_VB_SHOULD(VB_FIRST_PERSON_CAMERA, CVAR, {
        *should = false;

        Player* player = GET_PLAYER(gPlayState);
        PlayerUnkAA5 firstPersonMode = PLAYER_UNKAA5_1;

        if (gSaveContext.save.saveInfo.inventory.items[SLOT_PICTOGRAPH_BOX] == ITEM_PICTOGRAPH_BOX &&
            !CHECK_QUEST_ITEM(QUEST_PICTOGRAPH)) {
            firstPersonMode = PLAYER_UNKAA5_2;
        }

        if (player->tatlTextId == 0 && !func_80123420(player) &&
            CHECK_BTN_ALL(sPlayerControlInput->press.button, BTN_CUP) &&
            !func_80831814(player, gPlayState, firstPersonMode)) {
            Audio_PlaySfx(NA_SE_SY_ERROR);
        }
    });
}
