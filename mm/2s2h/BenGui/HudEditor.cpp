
#include "HudEditor.h"
#include "macros.h"
#include "2s2h/ShipInit.hpp"

extern "C" int16_t OTRGetRectDimensionFromLeftEdge(float v);
extern "C" int16_t OTRGetRectDimensionFromRightEdge(float v);

#define HUD_EDITOR_GLOBAL_SCALE_CVAR "gHudEditor.GlobalScale"

HudEditorElementID hudEditorActiveElement = HUD_EDITOR_ELEMENT_NONE;

// clang-format off
HudEditorElement hudEditorElements[HUD_EDITOR_ELEMENT_MAX] = {
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_B, "B Button", "B", 167, 17, 100, 255, 120, 255, "Buttons.B"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_C_LEFT, "C-Left Button", "CLeft", 227, 18, 255, 240, 0, 255, "Buttons.CLeft"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_C_DOWN, "C-Down Button", "CDown", 249, 34, 255, 240, 0, 255, "Buttons.CDown"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_C_RIGHT, "C-Right Button", "CRight", 271, 18, 255, 240, 0, 255, "Buttons.CRight"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_A, "A Button", "A", 191, 18, 100, 200, 255, 255, "Buttons.A"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_C_UP, "C-Up Button", "CUp", 254, 16, 255, 240, 0, 255, HUD_EDITOR_NO_COSMETIC),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_D_PAD, "D-Pad", "DPad", 271, 55, 255, 255, 255, 255, "Buttons.DPad"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_MINIMAP, "Minimap", "Minimap", 295, 220, 0, 255, 255, 160, "HUD.Minimap"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_START, "Start Button", "Start", 136, 17, 255, 130, 60, 255, "Buttons.Start"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_CARROT, "Horse Carrots", "Carrots", 160, 64, 236, 92, 41, 255, HUD_EDITOR_NO_COSMETIC),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_CLOCK, "Three Day Clock", "Clock", 160, 206, 255, 255, 255, 255, HUD_EDITOR_NO_COSMETIC),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_HEARTS, "Hearts", "Hearts", 30, 26, 255, 70, 50, 255, "HUD.Hearts"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_MAGIC_METER, "Magic", "Magic", 18, 34, 0, 200, 0, 255, "HUD.Magic"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_TIMERS, "Timers", "Timers", 26, 46, 255, 255, 255, 255, HUD_EDITOR_NO_COSMETIC),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_TIMERS_MOON_CRASH, "Timer - Skull Kid", "SkullKidTimer", 115, 200, 255, 255, 255, 255, HUD_EDITOR_NO_COSMETIC),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_MINIGAME_COUNTER, "Minigames", "Minigames", 20, 67, 255, 255, 255, 255, HUD_EDITOR_NO_COSMETIC),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_RUPEE_COUNTER, "Rupees", "Rupees", 26, 206, 200, 255, 100, 255, "HUD.RupeeIcon"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_KEY_COUNTER, "Keys", "Keys", 26, 190, 255, 255, 255, 255, "HUD.SmallKey"),
    HUD_EDITOR_ELEMENT(HUD_EDITOR_ELEMENT_SKULLTULA_COUNTER, "Skulltulas", "Skulltulas", 26, 190, 255, 255, 255, 255, HUD_EDITOR_NO_COSMETIC),
};
// clang-format on

extern "C" bool HudEditor_ShouldOverrideDraw() {
    return hudEditorActiveElement != HUD_EDITOR_ELEMENT_NONE &&
           CVarGetInteger(hudEditorElements[hudEditorActiveElement].modeCvar, HUD_EDITOR_ELEMENT_MODE_VANILLA) !=
               HUD_EDITOR_ELEMENT_MODE_VANILLA;
}

extern "C" void HudEditor_SetActiveElement(HudEditorElementID id) {
    hudEditorActiveElement = id;
}

extern "C" bool HudEditor_IsActiveElementHidden() {
    return hudEditorActiveElement != HUD_EDITOR_ELEMENT_NONE
               ? CVarGetInteger(hudEditorElements[hudEditorActiveElement].modeCvar, HUD_EDITOR_ELEMENT_MODE_VANILLA) ==
                     HUD_EDITOR_ELEMENT_MODE_HIDDEN
               : false;
}

static f32 HudEditor_GetGlobalScale() {
    f32 scale = CVarGetFloat(HUD_EDITOR_GLOBAL_SCALE_CVAR, 1.0f);
    return scale > 0.0f ? scale : 1.0f;
}

extern "C" f32 HudEditor_GetActiveElementScale() {
    return hudEditorActiveElement != HUD_EDITOR_ELEMENT_NONE
               ? CVarGetFloat(hudEditorElements[hudEditorActiveElement].scaleCvar, 1.0f) * HudEditor_GetGlobalScale()
               : 1.0f;
}

extern "C" void HudEditor_ModifyRectPosValuesFromBase(s16 baseX, s16 baseY, s16* rectLeft, s16* rectTop) {
    s16 offsetFromBaseX = *rectLeft - baseX;
    s16 offsetFromBaseY = *rectTop - baseY;
    f32 scale = HudEditor_GetActiveElementScale();

    *rectLeft = baseX + (offsetFromBaseX * scale);
    *rectTop = baseY + (offsetFromBaseY * scale);
}

void HudEditor_ModifyRectPosValuesFloat(f32* rectLeft, f32* rectTop) {
    f32 offsetFromBaseX = *rectLeft - hudEditorElements[hudEditorActiveElement].defaultX;
    f32 offsetFromBaseY = *rectTop - hudEditorElements[hudEditorActiveElement].defaultY;
    f32 scale = HudEditor_GetActiveElementScale();

    *rectLeft = CVarGetInteger(hudEditorElements[hudEditorActiveElement].xCvar,
                               hudEditorElements[hudEditorActiveElement].defaultX) +
                (offsetFromBaseX * scale);
    *rectTop = CVarGetInteger(hudEditorElements[hudEditorActiveElement].yCvar,
                              hudEditorElements[hudEditorActiveElement].defaultY) +
               (offsetFromBaseY * scale);

    if (CVarGetInteger(hudEditorElements[hudEditorActiveElement].modeCvar, HUD_EDITOR_ELEMENT_MODE_VANILLA) ==
        HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT) {
        *rectLeft = OTRGetRectDimensionFromLeftEdge(*rectLeft);
    } else if (CVarGetInteger(hudEditorElements[hudEditorActiveElement].modeCvar, HUD_EDITOR_ELEMENT_MODE_VANILLA) ==
               HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT) {
        *rectLeft = OTRGetRectDimensionFromRightEdge(*rectLeft);
    }
}

extern "C" void HudEditor_ModifyRectPosValues(s16* rectLeft, s16* rectTop) {
    f32 newLeft = *rectLeft;
    f32 newTop = *rectTop;

    HudEditor_ModifyRectPosValuesFloat(&newLeft, &newTop);

    *rectLeft = (s16)newLeft;
    *rectTop = (s16)newTop;
}

extern "C" void HudEditor_ModifyRectSizeValues(s16* rectWidth, s16* rectHeight) {
    f32 scale = HudEditor_GetActiveElementScale();

    *rectWidth *= scale;
    *rectHeight *= scale;
}

extern "C" void HudEditor_ModifyTextureStepValues(s16* dsdx, s16* dtdy) {
    f32 scale = HudEditor_GetActiveElementScale();

    *dsdx /= scale;
    *dtdy /= scale;
}

// Modify matrix values based on the identity matrix (0,0) centered on the screen
extern "C" void HudEditor_ModifyMatrixValues(f32* transX, f32* transY) {
    *transX = ((f32)SCREEN_WIDTH / 2) + *transX;
    *transY = ((f32)SCREEN_HEIGHT / 2) - *transY;

    HudEditor_ModifyRectPosValuesFloat(transX, transY);

    *transX = *transX - ((f32)SCREEN_WIDTH / 2);
    *transY = ((f32)SCREEN_HEIGHT / 2) - *transY;
}

extern "C" void HudEditor_ModifyKaleidoEquipAnimValues(s16* ulx, s16* uly, s16* shrinkRate) {
    // Kaleido values are a multiple of 10 on the identity matrix
    // Normalize them before passing to the modify matrix
    f32 transX = *ulx / 10;
    f32 transY = *uly / 10;

    HudEditor_ModifyMatrixValues(&transX, &transY);

    *ulx = transX * 10;
    *uly = transY * 10;

    f32 scale = HudEditor_GetActiveElementScale();
    // 320 is the vanilla start size, and 280 is the vanilla end size (or 160 for dpad)
    // So we apply the scale to 280 and subtract to get the shrink rate
    int16_t endAnimSize = hudEditorActiveElement == HUD_EDITOR_ELEMENT_D_PAD ? 160 : 280;
    *shrinkRate = 320 - (s16)(endAnimSize * scale);
}

extern "C" void HudEditor_ModifyDrawValuesFromBase(s16 baseX, s16 baseY, s16* rectLeft, s16* rectTop, s16* rectWidth,
                                                   s16* rectHeight, s16* dsdx, s16* dtdy) {
    HudEditor_ModifyRectPosValuesFromBase(baseX, baseY, rectLeft, rectTop);

    f32 scale = HudEditor_GetActiveElementScale();

    *rectWidth *= scale;
    *rectHeight *= scale;
    *dsdx /= scale;
    *dtdy /= scale;
}

extern "C" void HudEditor_ModifyDrawValues(s16* rectLeft, s16* rectTop, s16* rectWidth, s16* rectHeight, s16* dsdx,
                                           s16* dtdy) {
    HudEditor_ModifyRectPosValues(rectLeft, rectTop);

    f32 scale = HudEditor_GetActiveElementScale();

    *rectWidth *= scale;
    *rectHeight *= scale;
    *dsdx /= scale;
    *dtdy /= scale;
}

const char* modeNames[] = {
    "Vanilla (4:3)", "Hidden", "Movable (Align Center)", "Movable (Align Left)", "Movable (Align Right)",
};

const char* presetNames[] = {
    "Vanilla (4:3)",
    "Hidden",
    "Widescreen",
    "OoT Layout",
    "Pro Controller Layout",
};

static CosmeticOption& HudEditor_GetCosmeticOption(const char* cosmeticOptionId) {
    return cosmeticOptions.at(cosmeticOptionId);
}

static void HudEditor_SetElementLayout(HudEditorElementID id, HudEditorElementMode mode, int32_t x, int32_t y,
                                       f32 scale) {
    CVarSetInteger(hudEditorElements[id].modeCvar, mode);
    CVarSetInteger(hudEditorElements[id].xCvar, x);
    CVarSetInteger(hudEditorElements[id].yCvar, y);
    CVarSetFloat(hudEditorElements[id].scaleCvar, scale);
}

static void HudEditor_SetElementHidden(HudEditorElementID id) {
    CVarSetInteger(hudEditorElements[id].modeCvar, HUD_EDITOR_ELEMENT_MODE_HIDDEN);
}

namespace HudEditor {
enum Presets {
    VANILLA,
    HIDDEN,
    WIDESCREEN,
    OCARINA_OF_TIME,
    PRO_CONTROLLER,
};
};

void HudEditorWindow::DrawElement() {
    static HudEditor::Presets preset = HudEditor::Presets::VANILLA;
    if (UIWidgets::Combobox("Preset", &preset, presetNames)) {
        CVarClear(HUD_EDITOR_GLOBAL_SCALE_CVAR);
        for (int i = HUD_EDITOR_ELEMENT_B; i < HUD_EDITOR_ELEMENT_MAX; i++) {
            CVarClear(hudEditorElements[i].xCvar);
            CVarClear(hudEditorElements[i].yCvar);
            CVarClear(hudEditorElements[i].scaleCvar);
            CVarClear(hudEditorElements[i].modeCvar);
            // Also clear cosmetic colors for elements with mappings
            if (hudEditorElements[i].cosmeticOptionId != nullptr) {
                CosmeticOption& cosmeticElement = HudEditor_GetCosmeticOption(hudEditorElements[i].cosmeticOptionId);
                CVarClear(cosmeticElement.colorCvar);
                CVarClear(cosmeticElement.colorChangedCvar);
                ShipInit::Init(cosmeticElement.colorCvar);
                ShipInit::Init(cosmeticElement.colorChangedCvar);
            }
        }

        switch (preset) {
            case HudEditor::Presets::VANILLA: {
                break;
            }
            case HudEditor::Presets::HIDDEN: {
                for (int i = HUD_EDITOR_ELEMENT_B; i < HUD_EDITOR_ELEMENT_MAX; i++) {
                    CVarSetInteger(hudEditorElements[i].modeCvar, HUD_EDITOR_ELEMENT_MODE_HIDDEN);
                }
                break;
            }
            case HudEditor::Presets::WIDESCREEN: {
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_B].modeCvar, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_C_LEFT].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_C_DOWN].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_C_RIGHT].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_A].modeCvar, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_C_UP].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_D_PAD].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_MINIMAP].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_START].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_CARROT].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_43);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_CLOCK].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_43);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_HEARTS].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_MAGIC_METER].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_TIMERS].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_TIMERS_MOON_CRASH].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_43);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_MINIGAME_COUNTER].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_RUPEE_COUNTER].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_KEY_COUNTER].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT);
                CVarSetInteger(hudEditorElements[HUD_EDITOR_ELEMENT_SKULLTULA_COUNTER].modeCvar,
                               HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT);
                break;
            }
            case HudEditor::Presets::OCARINA_OF_TIME: {
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_B, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 218, 18,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_A, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 252, 18,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_C_LEFT, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 281, 18,
                                           0.85f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_C_RIGHT, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 307, 18,
                                           0.85f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_C_DOWN, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 293, 54,
                                           0.85f);
                HudEditor_SetElementHidden(HUD_EDITOR_ELEMENT_C_UP);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_D_PAD, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 304, 86,
                                           0.8f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_MINIMAP, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 295, 220,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_START, HUD_EDITOR_ELEMENT_MODE_MOVABLE_43, 160, 17,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_CARROT, HUD_EDITOR_ELEMENT_MODE_MOVABLE_43, 160, 64,
                                           1.0f);
                HudEditor_SetElementHidden(HUD_EDITOR_ELEMENT_CLOCK);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_HEARTS, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT, 30, 26,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_MAGIC_METER, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT, 18,
                                           42, 1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_TIMERS, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT, 26, 56,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_TIMERS_MOON_CRASH, HUD_EDITOR_ELEMENT_MODE_MOVABLE_43,
                                           115, 200, 1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_MINIGAME_COUNTER, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT,
                                           20, 67, 1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_RUPEE_COUNTER, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT, 26,
                                           206, 1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_KEY_COUNTER, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT, 26,
                                           190, 1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_SKULLTULA_COUNTER, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT,
                                           26, 190, 1.0f);
                break;
            }
            case HudEditor::Presets::PRO_CONTROLLER: {
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_C_UP, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 276, 16,
                                           0.85f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_C_LEFT, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 252, 40,
                                           0.85f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_C_RIGHT, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 300, 40,
                                           0.85f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_C_DOWN, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 276, 64,
                                           0.85f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_A, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 284, 82,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_B, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 252, 86,
                                           0.95f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_D_PAD, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 294, 128,
                                           0.8f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_MINIMAP, HUD_EDITOR_ELEMENT_MODE_MOVABLE_RIGHT, 295, 220,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_START, HUD_EDITOR_ELEMENT_MODE_MOVABLE_43, 160, 17,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_CARROT, HUD_EDITOR_ELEMENT_MODE_MOVABLE_43, 160, 64,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_CLOCK, HUD_EDITOR_ELEMENT_MODE_MOVABLE_43, 160, 206,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_HEARTS, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT, 30, 26,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_MAGIC_METER, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT, 18,
                                           34, 1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_TIMERS, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT, 26, 46,
                                           1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_TIMERS_MOON_CRASH, HUD_EDITOR_ELEMENT_MODE_MOVABLE_43,
                                           115, 200, 1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_MINIGAME_COUNTER, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT,
                                           20, 67, 1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_RUPEE_COUNTER, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT, 26,
                                           206, 1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_KEY_COUNTER, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT, 26,
                                           190, 1.0f);
                HudEditor_SetElementLayout(HUD_EDITOR_ELEMENT_SKULLTULA_COUNTER, HUD_EDITOR_ELEMENT_MODE_MOVABLE_LEFT,
                                           26, 190, 1.0f);
                break;
            }
        }
        Ship::Context::GetInstance()->GetWindow()->GetGui()->SaveConsoleVariablesOnNextTick();
    }

    UIWidgets::CVarSliderFloat("Global Scale", HUD_EDITOR_GLOBAL_SCALE_CVAR,
                               UIWidgets::FloatSliderOptions()
                                   .Min(0.25f)
                                   .Max(2.0f)
                                   .DefaultValue(1.0f)
                                   .ShowAdjustmentButtons(false)
                                   .Format("Global Scale: %.2fx")
                                   .LabelPosition(UIWidgets::LabelPosition::None));

    for (int i = HUD_EDITOR_ELEMENT_B; i < HUD_EDITOR_ELEMENT_MAX; i++) {
        ImGui::PushID(hudEditorElements[i].name);
        ImGui::SeparatorText(hudEditorElements[i].name);

        // Color picker - only enabled if this element has a cosmetic counterpart
        bool hasCosmeticMapping = hudEditorElements[i].cosmeticOptionId != nullptr;

        if (hasCosmeticMapping) {
            CosmeticOption& cosmeticElement = HudEditor_GetCosmeticOption(hudEditorElements[i].cosmeticOptionId);
            bool colorChanged = CVarGetInteger(cosmeticElement.colorChangedCvar, false);
            float defaultColor[4] = { cosmeticElement.defaultR / 255.0f, cosmeticElement.defaultG / 255.0f,
                                      cosmeticElement.defaultB / 255.0f, cosmeticElement.defaultA / 255.0f };
            float color[4] = { defaultColor[0], defaultColor[1], defaultColor[2], defaultColor[3] };

            if (colorChanged) {
                Color_RGBA8 changedColor = CVarGetColor(cosmeticElement.colorCvar, {});
                color[0] = (float)changedColor.r / 255;
                color[1] = (float)changedColor.g / 255;
                color[2] = (float)changedColor.b / 255;
                color[3] = (float)changedColor.a / 255;
            }

            if (ImGui::ColorEdit3("Color", color, ImGuiColorEditFlags_NoInputs | ImGuiColorEditFlags_NoLabel)) {
                Color_RGBA8 colorSelected;
                colorSelected.r = static_cast<uint8_t>(color[0] * 255.0f);
                colorSelected.g = static_cast<uint8_t>(color[1] * 255.0f);
                colorSelected.b = static_cast<uint8_t>(color[2] * 255.0f);
                colorSelected.a = static_cast<uint8_t>(255.0f);

                CVarSetColor(cosmeticElement.colorCvar, colorSelected);
                CVarSetInteger(cosmeticElement.colorChangedCvar, true);
                ShipInit::Init(cosmeticElement.colorCvar);
                ShipInit::Init(cosmeticElement.colorChangedCvar);
                Ship::Context::GetInstance()->GetWindow()->GetGui()->SaveConsoleVariablesOnNextTick();
            }
            ImGui::SameLine();
            if (ImGui::Button(ICON_FA_REFRESH)) {
                CVarClear(cosmeticElement.colorCvar);
                CVarClear(cosmeticElement.colorChangedCvar);
                ShipInit::Init(cosmeticElement.colorCvar);
                ShipInit::Init(cosmeticElement.colorChangedCvar);
                Ship::Context::GetInstance()->GetWindow()->GetGui()->SaveConsoleVariablesOnNextTick();
            }
        } else {
            // Disabled color picker for elements without cosmetic mappings
            ImGui::BeginDisabled();
            float defaultColor[4] = { hudEditorElements[i].defaultR / 255.0f, hudEditorElements[i].defaultG / 255.0f,
                                      hudEditorElements[i].defaultB / 255.0f, hudEditorElements[i].defaultA / 255.0f };
            ImGui::ColorEdit3("Color", defaultColor, ImGuiColorEditFlags_NoInputs | ImGuiColorEditFlags_NoLabel);
            if (ImGui::IsItemHovered(ImGuiHoveredFlags_AllowWhenDisabled)) {
                ImGui::SetTooltip("%s", "Color customization is not yet available for this element.");
            }
            ImGui::SameLine();
            ImGui::Button(ICON_FA_REFRESH);
            ImGui::EndDisabled();
        }
        ImGui::SameLine();
        if (UIWidgets::CVarCombobox(
                "Mode", hudEditorElements[i].modeCvar, modeNames,
                UIWidgets::ComboboxOptions().LabelPosition(UIWidgets::LabelPosition::None))) {
            CVarClear(hudEditorElements[i].xCvar);
            CVarClear(hudEditorElements[i].yCvar);
            CVarClear(hudEditorElements[i].scaleCvar);
        }
        if (CVarGetInteger(hudEditorElements[i].modeCvar, HUD_EDITOR_ELEMENT_MODE_VANILLA) >=
            HUD_EDITOR_ELEMENT_MODE_MOVABLE_43) {
            if (ImGui::BeginTable("##table", 3,
                                  ImGuiTableFlags_NoSavedSettings | ImGuiTableFlags_NoBordersInBody |
                                      ImGuiTableFlags_SizingStretchSame)) {
                ImGui::TableNextColumn();
                UIWidgets::CVarSliderInt("X", hudEditorElements[i].xCvar,
                                         UIWidgets::IntSliderOptions()
                                             .Min(-10)
                                             .Max(330)
                                             .DefaultValue(hudEditorElements[i].defaultX)
                                             .ShowAdjustmentButtons(false)
                                             .Format("X: %d")
                                             .LabelPosition(UIWidgets::LabelPosition::None));
                ImGui::TableNextColumn();
                UIWidgets::CVarSliderInt("Y", hudEditorElements[i].yCvar,
                                         UIWidgets::IntSliderOptions()
                                             .Min(-10)
                                             .Max(250)
                                             .DefaultValue(hudEditorElements[i].defaultY)
                                             .ShowAdjustmentButtons(false)
                                             .Format("Y: %d")
                                             .LabelPosition(UIWidgets::LabelPosition::None));
                ImGui::TableNextColumn();
                UIWidgets::CVarSliderFloat("Scale", hudEditorElements[i].scaleCvar,
                                           UIWidgets::FloatSliderOptions()
                                               .Min(0.25f)
                                               .Max(4.0f)
                                               .DefaultValue(1.0f)
                                               .ShowAdjustmentButtons(false)
                                               .Format("Scale: %.2f")
                                               .LabelPosition(UIWidgets::LabelPosition::None));
                ImGui::EndTable();
            }
        }
        ImGui::PopID();
    }
}
