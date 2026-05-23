#include <libultraship/bridge.h>
#include "2s2h/GameInteractor/GameInteractor.h"

extern "C" {
#include "variables.h"
extern Input* sPlayerControlInput;
}

#define CVAR_SPEED_MODIFIER_MODE_NAME "gCheats.SpeedModifier.Mode"
#define CVAR_SPEED_MODIFIER_MODE CVarGetInteger(CVAR_SPEED_MODIFIER_MODE_NAME, 0)
#define CVAR_SPEED_MODIFIER_VALUE CVarGetFloat("gCheats.SpeedModifier.Value", 1.0f)
#define CVAR_SPEED_MODIFIER_BTN CVarGetInteger("gCheats.SpeedModifier.Btn", BTN_CUSTOM_MODIFIER1)

static bool sSpeedModifierButtonActive = false;

static f32 GetSpeedModifierValue() {
    return CLAMP(CVAR_SPEED_MODIFIER_VALUE, 0.1f, 6.0f);
}

void RegisterLinkSpeedModifier() {
    sSpeedModifierButtonActive = false;

    REGISTER_VB_SHOULD(VB_SPEED_MODIFIER_WALK, {
        if (CVAR_SPEED_MODIFIER_MODE != 0) {
            f32* speedTarget = va_arg(args, f32*);
            if (CVAR_SPEED_MODIFIER_MODE == 1 || sSpeedModifierButtonActive) {
                *speedTarget *= GetSpeedModifierValue();
            }
        }
    });

    REGISTER_VB_SHOULD(VB_SPEED_MODIFIER_SWIM, {
        if (CVAR_SPEED_MODIFIER_MODE != 0 && sPlayerControlInput != NULL) {
            f32* incrStep = va_arg(args, f32*);
            f32* maxSpeed = va_arg(args, f32*);
            f32* speed = va_arg(args, f32*);
            f32* speedTarget = va_arg(args, f32*);

            if (CVAR_SPEED_MODIFIER_MODE == 1 || sSpeedModifierButtonActive) {
                f32 swimModifier = GetSpeedModifierValue();
                *maxSpeed *= swimModifier;
                Math_AsymStepToF(speed, *speedTarget * 0.8f * swimModifier, *incrStep,
                                 (fabsf(*speed) * 0.02f) + 0.05f);
                *should = false;
            }
        }
    });

    GameInteractor::Instance->RegisterGameHook<GameInteractor::OnPassPlayerInputs>([](Input* input) {
        if (CVAR_SPEED_MODIFIER_MODE == 2) {
            sSpeedModifierButtonActive = CHECK_BTN_ALL(input->cur.button, CVAR_SPEED_MODIFIER_BTN);
        } else if (CVAR_SPEED_MODIFIER_MODE == 3 &&
                   CHECK_BTN_ALL(input->cur.button, CVAR_SPEED_MODIFIER_BTN) &&
                   CHECK_BTN_ANY(input->press.button, CVAR_SPEED_MODIFIER_BTN)) {
            sSpeedModifierButtonActive = !sSpeedModifierButtonActive;
        } else if (CVAR_SPEED_MODIFIER_MODE < 2) {
            sSpeedModifierButtonActive = false;
        }
    });
}
