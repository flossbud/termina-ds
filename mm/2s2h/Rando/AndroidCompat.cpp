#include "global.h"
#include "gfx.h"
#include "overlays/actors/ovl_Boss_Hakugin/z_boss_hakugin.h"
#include "overlays/actors/ovl_En_Kitan/z_en_kitan.h"

extern "C" {
void Player_TalkWithPlayer(PlayState* play, Actor* actor);
void func_80837B60(PlayState* play, Player* player);
s32 func_80832558(PlayState* play, Player* player, AfterPutAwayFunc afterPutAwayFunc);
void func_80C095C8(EnKitan* enKitan, PlayState* play);
}

void UpdateGameTime(u16 gameTime) {
    gSaveContext.save.time = gameTime;
}

extern "C" void Player_StartTalking(PlayState* play, Actor* actor) {
    Player_TalkWithPlayer(play, actor);
}

extern "C" void Player_SetupTalk(PlayState* play, Player* player) {
    func_80837B60(play, player);
}

extern "C" s32 Player_SetupWaitForPutAway(PlayState* play, Player* player, AfterPutAwayFunc afterPutAwayFunc) {
    return func_80832558(play, player, afterPutAwayFunc);
}

extern "C" Gfx* EnKnight_BuildEmptyDL(GraphicsContext* gfxCtx) {
    return gEmptyDL;
}

extern "C" void BossHakugin_DrawIce(BossHakugin* boss, PlayState* play) {
}

extern "C" void EnKitan_TalkAfterGivingPrize(EnKitan* enKitan, PlayState* play) {
    func_80C095C8(enKitan, play);
}
