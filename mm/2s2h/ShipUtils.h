#ifndef SHIP_UTILS_H
#define SHIP_UTILS_H

#include <libultraship/libultraship.h>
#include "PR/ultratypes.h"
#include "macros.h"

#define MORNING_TIME 0x4000
#define ZERO_DAY_START(time) (((u16)((time)-MORNING_TIME) % DAY_LENGTH))

#ifdef __cplusplus
#include <array>
#include <imgui.h>
#include <map>
#include <string>

void LoadGuiTextures();
ImVec4 Ship_GetItemColorTint(uint32_t itemId);
std::string convertEnumToReadableName(const std::string& input);
std::string Ship_RemoveSpecialCharacters(const std::string& str);
uint32_t Ship_Hash(std::string str);
extern std::array<const char*, 11> digitList;
extern std::string Ship_FormatTimeDisplay(uint32_t value);
extern std::map<uint32_t, ImVec4> itemColorMap;

extern "C" {
#endif

struct PlayState;
struct Actor;

f32 Ship_GetExtendedAspectRatioMultiplier();
void Ship_ExtendedCullingActorAdjustProjectedZ(Actor* actor);
void Ship_ExtendedCullingActorAdjustProjectedX(Actor* actor);
void Ship_ExtendedCullingActorRestoreProjectedPos(PlayState* play, Actor* actor);
const char* Ship_GetSceneName(s16 sceneId);

bool Ship_IsCStringEmpty(const char* str);
void Ship_CreateQuadVertexGroup(Vtx* vtxList, s32 xStart, s32 yStart, s32 width, s32 height, u8 flippedH);
f32 Ship_GetCharFontWidthNES(u8 character);
TexturePtr Ship_GetCharFontTextureNES(u8 character);
void Ship_Random_Seed(u64 seed);
s32 Ship_Random(s32 min, s32 max);

#ifdef __cplusplus
}
#endif

#endif // SHIP_UTILS_H
