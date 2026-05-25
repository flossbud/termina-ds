#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

#define CVAR_NAME "gEnhancements.DifficultyOptions.DamageMultiplier"
#define CVAR CVarGetInteger(CVAR_NAME, 0)

void RegisterDamageMultiplier() {
    COND_VB_SHOULD(VB_MULTIPLY_INFLICTED_DMG, CVAR, {
        s32* damage = va_arg(args, s32*);
        *damage <<= CVAR;
    });
}

