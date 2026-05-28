#include "ClockShuffle.h"
#include "Rando/Rando.h"
#include "Rando/Logic/Logic.h"
#include "2s2h/GameInteractor/GameInteractor.h"
#include "2s2h/CustomMessage/CustomMessage.h"

extern "C" {
#include "variables.h"
#include "functions.h"
#include "overlays/actors/ovl_En_Test4/z_en_test4.h"
#include "overlays/actors/ovl_Obj_Tokei_Step/z_obj_tokei_step.h"

void func_80A42198(EnTest4* thisx);
void func_80A425E4(EnTest4* thisx, PlayState* play);
void ObjTokeiStep_SetupOpen(ObjTokeiStep* thisx);
void ObjTokeiStep_DrawOpen(Actor* thisx, PlayState* play);
void ObjTokeiStep_DoNothing(ObjTokeiStep* thisx, PlayState* play);
}

static constexpr u16 DAWN_TIME = CLOCK_TIME(6, 0);
static constexpr u16 DUSK_TIME = CLOCK_TIME(18, 0);

int Rando::ClockItems::GetHalfDayIndexFromClockItem(RandoItemId clockItemId) {
    switch (clockItemId) {
        case RI_TIME_DAY_1:
            return HALF_DAY1_DAY;
        case RI_TIME_NIGHT_1:
            return HALF_DAY1_NIGHT;
        case RI_TIME_DAY_2:
            return HALF_DAY2_DAY;
        case RI_TIME_NIGHT_2:
            return HALF_DAY2_NIGHT;
        case RI_TIME_DAY_3:
            return HALF_DAY3_DAY;
        case RI_TIME_NIGHT_3:
            return HALF_DAY3_NIGHT;
        default:
            return INVALID;
    }
}

RandoItemId Rando::ClockItems::GetClockItemFromHalfDayIndex(int halfDayIndex) {
    static const RandoItemId clockItemMap[] = {
        RI_TIME_DAY_1, RI_TIME_NIGHT_1, RI_TIME_DAY_2, RI_TIME_NIGHT_2, RI_TIME_DAY_3, RI_TIME_NIGHT_3,
    };

    if (halfDayIndex < 0 || halfDayIndex >= HALF_COUNT) {
        return RI_UNKNOWN;
    }
    return clockItemMap[halfDayIndex];
}

u8 Rando::ClockItems::GetAllOwnedHalfDaysMask() {
    u8 ownedMask = 0;
    for (int i = 0; i < HALF_COUNT; ++i) {
        if (Rando::Logic::OwnsClockHalfDay(i)) {
            ownedMask |= (1 << i);
        }
    }
    return ownedMask;
}

int Rando::ClockItems::FindOwnedHalfDay(bool fromEnd) {
    if (fromEnd) {
        for (int i = HALF_COUNT - 1; i >= 0; --i) {
            if (Rando::Logic::OwnsClockHalfDay(i)) {
                return i;
            }
        }
    } else {
        for (int i = 0; i < HALF_COUNT; ++i) {
            if (Rando::Logic::OwnsClockHalfDay(i)) {
                return i;
            }
        }
    }
    return INVALID;
}

bool Rando::ClockItems::IsClockItem(RandoItemId itemId) {
    return (itemId >= RI_TIME_DAY_1 && itemId <= RI_TIME_NIGHT_3) || itemId == RI_TIME_PROGRESSIVE;
}

bool Rando::ClockItems::IsDayClock(RandoItemId itemId) {
    return itemId == RI_TIME_DAY_1 || itemId == RI_TIME_DAY_2 || itemId == RI_TIME_DAY_3;
}

static int FindNextOwnedHalfDayAfter(int startHalfDay, u8 ownedMask) {
    if (startHalfDay < 0 || startHalfDay >= Rando::ClockItems::HALF_COUNT) {
        return Rando::ClockItems::TERMINAL_STATE;
    }

    for (int halfDayIndex = startHalfDay + 1; halfDayIndex < Rando::ClockItems::HALF_COUNT; ++halfDayIndex) {
        if (ownedMask & (1 << halfDayIndex)) {
            return halfDayIndex;
        }
    }

    return Rando::ClockItems::TERMINAL_STATE;
}

static bool IsNight(u16 time) {
    return (time < DAWN_TIME) || (time >= DUSK_TIME);
}

static u16 GetConfiguredTerminalTime() {
    int terminalMinutes = RANDO_SAVE_OPTIONS[RO_CLOCK_TERMINAL_TIME];
    return CLOCK_TIME(0, terminalMinutes);
}

static bool IsInTerminalRange(s32 day, u16 time) {
    if (day != 3) {
        return false;
    }

    u16 terminalTime = GetConfiguredTerminalTime();

    if (terminalTime >= DUSK_TIME) {
        return (time >= terminalTime) || (time < DAWN_TIME);
    }

    return (time >= terminalTime && time < DAWN_TIME);
}

static int GetHalfDayIndexFromTime(s32 day, u16 time) {
    if (day < 1) {
        return Rando::ClockItems::HALF_DAY1_DAY;
    }

    int halfDay = (day - 1) * 2;
    if (IsNight(time)) {
        halfDay++;
    }
    return halfDay;
}

static int GetCurrentHalfDayIndex() {
    u16 currentTime = gSaveContext.save.time;
    s32 currentDay = gSaveContext.save.day;

    if (currentDay >= 4 || currentDay == 0) {
        return Rando::ClockItems::TERMINAL_STATE;
    }

    if (IsInTerminalRange(currentDay, currentTime)) {
        return Rando::ClockItems::TERMINAL_STATE;
    }

    return GetHalfDayIndexFromTime(currentDay, currentTime);
}

bool Rando::ClockShuffle::IsTimeOwnedForClockShuffle(s32 day, u16 time) {
    if (!RANDO_SAVE_OPTIONS[RO_CLOCK_SHUFFLE]) {
        return true;
    }
    if (day < 1 || day > 3) {
        return true;
    }
    if (IsInTerminalRange(day, time)) {
        return true;
    }

    return Rando::Logic::OwnsClockHalfDay(GetHalfDayIndexFromTime(day, time));
}

std::string Rando::ClockShuffle::GetTimeDescriptionForMessage(s32 day, u16 time) {
    if (IsInTerminalRange(day, time)) {
        return "%rFinal Hours%w";
    }

    bool isNight = IsNight(time);
    std::string description = isNight ? "%rNight of the " : "%rDawn of the ";

    if (day == 1) {
        description += "First";
    } else if (day == 2) {
        description += "Second";
    } else if (day == 3) {
        description += "Third";
    } else {
        description += "Unknown";
    }

    description += " Day%w";
    return description;
}

void Rando::ClockShuffle::SetTimeToHalfDayStart(int halfDayIndex) {
    if (halfDayIndex < 0 || halfDayIndex >= Rando::ClockItems::HALF_COUNT) {
        return;
    }

    gSaveContext.save.day = (halfDayIndex / 2) + 1;
    gSaveContext.save.time = (halfDayIndex & 1) ? DUSK_TIME : DAWN_TIME;
}

static std::string GetHalfDayDescriptionForMessage(int halfDayIndex) {
    if (halfDayIndex == Rando::ClockItems::TERMINAL_STATE) {
        return "%rFinal Hours%w";
    }

    int targetDay = (halfDayIndex / 2) + 1;
    bool isNight = (halfDayIndex & 1);
    return Rando::ClockShuffle::GetTimeDescriptionForMessage(targetDay, isNight ? DUSK_TIME : DAWN_TIME);
}

static void ProcessClockShuffleMessage(u16* textId, bool* loadFromMessageTable, bool isSongOfTime) {
    auto entry = CustomMessage::LoadVanillaMessageTableEntry(*textId);
    int targetHalfDay;

    if (isSongOfTime) {
        targetHalfDay = Rando::ClockItems::FindOwnedHalfDay(false);
    } else {
        int currentHalfDay = GetCurrentHalfDayIndex();
        u8 ownedHalfDaysMask = Rando::ClockItems::GetAllOwnedHalfDaysMask();
        targetHalfDay = FindNextOwnedHalfDayAfter(currentHalfDay, ownedHalfDaysMask);
    }

    std::string destinationText = GetHalfDayDescriptionForMessage(targetHalfDay);
    if (isSongOfTime) {
        entry.msg = "Save and return to " + destinationText + "?\n%gYes\nNo\xC2";
    } else {
        entry.msg = "Time moves strangely...\nProceed to " + destinationText + "?\n%gYes\nNo\xC2";
    }

    CustomMessage::LoadCustomMessageIntoFont(entry);
    *loadFromMessageTable = false;
}

static void EnforceOwnedTime() {
    bool isCycleStart =
        (gSaveContext.save.day == 0 || (gSaveContext.save.day == 1 && gSaveContext.save.time == DAWN_TIME));
    bool isPendingDayTelop = (CHECK_EVENTINF(EVENTINF_TRIGGER_DAYTELOP) && gSaveContext.respawnFlag == -4);

    if (!isCycleStart && !isPendingDayTelop) {
        return;
    }

    int targetHalfDay = 0;
    if (isPendingDayTelop) {
        u8 nextDay = gSaveContext.save.day + 1;
        targetHalfDay = (nextDay < 1) ? 0 : ((nextDay - 1) * 2);
    }

    int validHalfDay = Rando::ClockItems::TERMINAL_STATE;
    if (Rando::Logic::OwnsClockHalfDay(targetHalfDay)) {
        validHalfDay = targetHalfDay;
    } else {
        for (int i = targetHalfDay + 1; i < Rando::ClockItems::HALF_COUNT; ++i) {
            if (Rando::Logic::OwnsClockHalfDay(i)) {
                validHalfDay = i;
                break;
            }
        }
    }

    if (validHalfDay != Rando::ClockItems::TERMINAL_STATE) {
        if (validHalfDay == targetHalfDay) {
            return;
        }

        if (validHalfDay & 1) {
            gSaveContext.save.day = (validHalfDay / 2) + 1;
            gSaveContext.save.time = DUSK_TIME;
        } else {
            gSaveContext.save.day = validHalfDay / 2;
            gSaveContext.save.time = DAWN_TIME - 1;
        }
    } else {
        gSaveContext.save.day = 3;
        gSaveContext.save.time = GetConfiguredTerminalTime();
    }

    if (isPendingDayTelop) {
        CLEAR_EVENTINF(EVENTINF_TRIGGER_DAYTELOP);
        gSaveContext.respawnFlag = -8;
    }
}

static bool SceneNeedsReloadForTimeSkip(s16 sceneId) {
    return sceneId == SCENE_BOWLING;
}

static void ForceSceneReload() {
    Player* player = GET_PLAYER(gPlayState);

    gPlayState->nextEntrance = gSaveContext.save.entrance;
    gPlayState->transitionTrigger = TRANS_TRIGGER_START;
    gPlayState->transitionType = TRANS_TYPE_FADE_BLACK_FAST;

    Play_SetRespawnData(&gPlayState->state, RESPAWN_MODE_RETURN, gSaveContext.save.entrance,
                        gPlayState->roomCtx.curRoom.num, PLAYER_PARAMS(0xFF, PLAYER_INITMODE_B), &player->unk_3C0,
                        player->unk_3CC);

    gSaveContext.nextTransitionType = TRANS_TYPE_FADE_BLACK;
    gSaveContext.respawnFlag = 2;
}

static bool CheckSkippedTime(u8* day, u16* time) {
    if (gPlayState == NULL || gPlayState->envCtx.sceneTimeSpeed == 0 || Play_InCsMode(gPlayState) || *day >= 4) {
        return false;
    }

    if (IsInTerminalRange(*day, *time)) {
        return false;
    }

    int currentHalfDay = GetHalfDayIndexFromTime(*day, *time);
    if (Rando::Logic::OwnsClockHalfDay(currentHalfDay)) {
        return false;
    }

    int nextHalfDay = FindNextOwnedHalfDayAfter(currentHalfDay, Rando::ClockItems::GetAllOwnedHalfDaysMask());
    if (nextHalfDay == Rando::ClockItems::TERMINAL_STATE) {
        *day = 3;
        *time = GetConfiguredTerminalTime();
    } else {
        *day = (nextHalfDay / 2) + 1;
        *time = (nextHalfDay & 1) ? DUSK_TIME : DAWN_TIME;
    }

    return true;
}

static bool CheckAndSkipUnownedTime(Actor* timeActor) {
    EnTest4* enTest4 = (EnTest4*)timeActor;
    u8 day = gSaveContext.save.day;
    u16 time = gSaveContext.save.time + CLOCK_TIME_MINUTE;

    if (IsNight(enTest4->unk_146) && !IsNight(time)) {
        day++;
    }

    if (day >= 4 || !CheckSkippedTime(&day, &time)) {
        return false;
    }

    gSaveContext.save.day = day;
    gSaveContext.save.time = time;

    if (SceneNeedsReloadForTimeSkip(gPlayState->sceneId)) {
        ForceSceneReload();
        return true;
    }

    gWeatherMode = WEATHER_MODE_CLEAR;
    gPlayState->envCtx.lightningState = LIGHTNING_OFF;

    if (time == DAWN_TIME) {
        enTest4->csIdIndex = 0;
        gSaveContext.save.day--;
    } else {
        enTest4->csIdIndex = (time == DUSK_TIME) ? 1 : (IsNight(time) ? 0 : 1);
        Interface_NewDay(gPlayState, gSaveContext.save.day);
        gPlayState->numSetupActors = -gPlayState->numSetupActors;

        enTest4->lastBellTime = time;
        if (gSaveContext.save.day == 3) {
            func_80A42198(enTest4);
        } else {
            func_80A425E4(enTest4, gPlayState);
        }
    }

    if (gPlayState->sequenceCtx.ambienceId != AMBIENCE_ID_13) {
        SEQCMD_STOP_SEQUENCE(SEQ_PLAYER_AMBIENCE, 0);
        SEQCMD_STOP_SEQUENCE(SEQ_PLAYER_BGM_MAIN, 240);
        gSaveContext.seqId = (u8)NA_BGM_DISABLED;
        gSaveContext.ambienceId = AMBIENCE_ID_DISABLED;
        Environment_PlaySceneSequence(gPlayState);
    }

    gSaveContext.screenScaleFlag = false;
    gSaveContext.screenScale = 1000.0f;

    if (gSaveContext.save.day == 3 && time < DAWN_TIME) {
        ObjTokeiStep* objTokeiStep = (ObjTokeiStep*)Actor_FindNearby(
            gPlayState, &GET_PLAYER(gPlayState)->actor, ACTOR_OBJ_TOKEI_STEP, ACTORCAT_BG, 99999.9f);
        if (objTokeiStep != NULL && objTokeiStep->actionFunc == ObjTokeiStep_DoNothing) {
            objTokeiStep->dyna.actor.draw = ObjTokeiStep_DrawOpen;
            ObjTokeiStep_SetupOpen(objTokeiStep);
        }
    }

    enTest4->unk_146 = (time == DAWN_TIME) ? (time - CLOCK_TIME_MINUTE) : time;
    return true;
}

void Rando::ClockShuffle::OnFileLoad() {
    bool shouldRegister = IS_RANDO && RANDO_SAVE_OPTIONS[RO_CLOCK_SHUFFLE];

    if (shouldRegister) {
        EnforceOwnedTime();
    }

    COND_HOOK(OnPlayDestroy, shouldRegister, []() { EnforceOwnedTime(); });

    COND_ID_HOOK(ShouldActorUpdate, ACTOR_EN_TEST4, shouldRegister, [](Actor* actor, bool* should) {
        if (!CHECK_EVENTINF(EVENTINF_17) && CheckAndSkipUnownedTime(actor)) {
            *should = false;
            return;
        }
        *should = true;
    });

    COND_ID_HOOK(OnOpenText, 0x1B8A, shouldRegister, [](u16* textId, bool* loadFromMessageTable) {
        ProcessClockShuffleMessage(textId, loadFromMessageTable, true);
    });

    auto onDoubleTime = [](u16* textId, bool* loadFromMessageTable) {
        ProcessClockShuffleMessage(textId, loadFromMessageTable, false);
    };

    COND_ID_HOOK(OnOpenText, 0x1B91, shouldRegister, onDoubleTime);
    COND_ID_HOOK(OnOpenText, 0x1B90, shouldRegister, onDoubleTime);
    COND_ID_HOOK(OnOpenText, 0x1B8F, shouldRegister, onDoubleTime);
    COND_ID_HOOK(OnOpenText, 0x1B92, shouldRegister, onDoubleTime);
    COND_ID_HOOK(OnOpenText, 0x1B8E, shouldRegister, onDoubleTime);

    COND_VB_SHOULD(VB_TIME_UNTIL_MOON_CRASH_CALCULATION, shouldRegister, {
        *should = false;
        u32* timeVar = va_arg(args, u32*);
        u8 ownedHalfDaysMask = Rando::ClockItems::GetAllOwnedHalfDaysMask();
        u32 totalHours = 0;

        for (int halfDayIndex = 0; halfDayIndex < Rando::ClockItems::HALF_COUNT; ++halfDayIndex) {
            if (ownedHalfDaysMask & (1 << halfDayIndex)) {
                totalHours += 12;
            }
        }

        bool shouldIncludeTerminalHours = (GetCurrentHalfDayIndex() == Rando::ClockItems::TERMINAL_STATE) ||
                                          !Rando::Logic::OwnsClockHalfDay(Rando::ClockItems::HALF_DAY3_NIGHT);

        if (shouldIncludeTerminalHours) {
            u16 terminalTime = GetConfiguredTerminalTime();
            u32 terminalZeroed = ZERO_DAY_START(terminalTime);
            u32 timeDiff = (DAY_LENGTH - terminalZeroed) % DAY_LENGTH;
            u32 terminalHours = (timeDiff + CLOCK_TIME_HOUR - 1) / CLOCK_TIME_HOUR;
            totalHours += terminalHours;
        }

        *timeVar = totalHours * CLOCK_TIME_HOUR;
    });
}
