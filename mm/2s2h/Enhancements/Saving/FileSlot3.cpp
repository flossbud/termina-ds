#include <libultraship/bridge.h>
#include "2s2h/ShipInit.hpp"

extern "C" {
#include "src/overlays/gamestates/ovl_file_choose/z_file_select.h"

extern FileSelectState* gFileSelectState;
}

#define CVAR_NAME "gEnhancements.Saving.FileSlot3"

void RegisterFileSlot3() {
    // Reset file select so save metadata and visible slots refresh immediately after changing the option.
    if (gFileSelectState != NULL) {
        STOP_GAMESTATE(&gFileSelectState->state);
        SET_NEXT_GAMESTATE(&gFileSelectState->state, FileSelect_Init, sizeof(FileSelectState));
    }
}

static RegisterShipInitFunc initFunc(RegisterFileSlot3, { CVAR_NAME });
