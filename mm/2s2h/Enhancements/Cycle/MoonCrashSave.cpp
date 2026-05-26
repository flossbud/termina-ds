#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"
#include "2s2h/Enhancements/Saving/SavingEnhancements.h"

extern "C" {
#include "variables.h"
}

void RegisterMoonCrashSave() {
    GameInteractor::Instance->RegisterGameHook<GameInteractor::BeforeMoonCrashSaveReset>([]() {
        if (!CVarGetInteger("gEnhancements.Cycle.SaveOnMoonCrash", 0)) {
            return;
        }

        SavingEnhancements_AdvancePlaytime();
        Sram_SaveEndOfCycle(gPlayState);
        func_8014546C(&gPlayState->sramCtx);

        if (gSaveContext.fileNum != 0xFF) {
            Sram_SetFlashPagesDefault(&gPlayState->sramCtx,
                                      gFlashSaveStartPages[gSaveContext.fileNum * FLASH_SAVE_MAIN_MULTIPLIER],
                                      gFlashSpecialSaveNumPages[gSaveContext.fileNum * FLASH_SAVE_MAIN_MULTIPLIER]);
            Sram_StartWriteToFlashDefault(&gPlayState->sramCtx);
        }
    });
}
