#include "ShipUtils.h"
#include <libultraship/libultraship.h>
#include "assets/2s2h_assets.h"
#include "assets/archives/icon_item_24_static/icon_item_24_static_yar.h"
#include "assets/archives/icon_item_static/icon_item_static_yar.h"
#include "assets/interface/icon_item_dungeon_static/icon_item_dungeon_static.h"
#include "assets/interface/icon_item_field_static/icon_item_field_static.h"
#include "assets/interface/parameter_static/parameter_static.h"
#include "assets/objects/gameplay_keep/gameplay_keep.h"

#include <cassert>
#include <algorithm>
#include <array>
#include <bit>
#include <cctype>
#include <cstdint>
#include <map>
#include <random>
#include <sstream>
#include <unordered_map>
#include <vector>

extern "C" {
#include "z64.h"
#include "functions.h"
#include "macros.h"

extern float OTRGetAspectRatio();

extern f32 sNESFontWidths[160];
extern const char* fontTbl[156];
extern TexturePtr gItemIcons[131];
extern TexturePtr gQuestIcons[14];
extern TexturePtr gBombersNotebookPhotos[24];
}

constexpr f32 fourByThree = 4.0f / 3.0f;

#define DEFINE_SCENE(_name, enumValue, _textId, _drawConfig, _restrictionFlags, _persistentCycleFlags, \
                     _entranceSceneId, _betterMapSelectIndex, humanName)                               \
    { enumValue, humanName },
#define DEFINE_SCENE_UNSET(_enumValue)

static std::unordered_map<s16, const char*> sceneNames = {
#include "tables/scene_table.h"
};

#undef DEFINE_SCENE
#undef DEFINE_SCENE_UNSET

// These textures are not in existing lists that we iterate over.
std::array<const char*, 21> miscellaneousTextures = {
    gArcheryScoreIconTex,
    gBarrelTrackerIcon,
    gChestTrackerIcon,
    gCrateTrackerIcon,
    gDungeonStrayFairyGreatBayIconTex,
    gDungeonStrayFairySnowheadIconTex,
    gDungeonStrayFairyStoneTowerIconTex,
    gDungeonStrayFairyWoodfallIconTex,
    gPotTrackerIcon,
    gQuestIconGoldSkulltulaTex,
    gMagicArrowEquipEffectTex,
    gRupeeCounterIconTex,
    gStrayFairyGreatBayIconTex,
    gStrayFairySnowheadIconTex,
    gStrayFairyStoneTowerIconTex,
    gStrayFairyWoodfallIconTex,
    gTimerClockIconTex,
    gWorldMapOwlFaceTex,
    gameplay_keep_Tex_053140,
    gDungeonMapSkullTex,
    gPauseUnusedCursorTex,
};

std::array<const char*, 11> digitList = { gCounterDigit0Tex, gCounterDigit1Tex, gCounterDigit2Tex, gCounterDigit3Tex,
                                          gCounterDigit4Tex, gCounterDigit5Tex, gCounterDigit6Tex, gCounterDigit7Tex,
                                          gCounterDigit8Tex, gCounterDigit9Tex, gCounterColonTex };

std::map<uint32_t, ImVec4> itemColorMap = {
    { ITEM_SONG_SONATA, ImVec4(0.588f, 1.0f, 0.392f, 1.0f) },
    { ITEM_SONG_LULLABY, ImVec4(1.0f, 0.313f, 0.156f, 1.0f) },
    { ITEM_SONG_NOVA, ImVec4(0.392f, 0.588f, 1.0f, 1.0f) },
    { ITEM_SONG_ELEGY, ImVec4(1.0f, 0.627f, 0.0f, 1.0f) },
    { ITEM_SONG_OATH, ImVec4(1.0f, 0.392f, 1.0f, 1.0f) },
    { ITEM_SONG_LULLABY_INTRO, ImVec4(1.0f, 0.313f, 0.156f, 1.0f) },
};

std::string Ship_FormatTimeDisplay(uint32_t value) {
    uint32_t sec = value / 10;
    uint32_t hh = sec / 3600;
    uint32_t mm = (sec - hh * 3600) / 60;
    uint32_t ss = sec - hh * 3600 - mm * 60;
    uint32_t ds = value % 10;
    return fmt::format("{}:{:0>2}:{:0>2}.{}", hh, mm, ss, ds);
}

// Gets the additional ratio of the screen compared to the original 4:3 ratio, clamping to 1 if smaller
extern "C" f32 Ship_GetExtendedAspectRatioMultiplier() {
    f32 currentRatio = OTRGetAspectRatio();
    return MAX(currentRatio / fourByThree, 1.0f);
}

// Enables Extended Culling options on specific actors by applying an inverse ratio of the draw distance slider
// to the projected Z value of the actor. This tricks distance checks without having to replace hardcoded values.
// Requires that Ship_ExtendedCullingActorRestoreProjectedPos is called within the same function scope.
extern "C" void Ship_ExtendedCullingActorAdjustProjectedZ(Actor* actor) {
    s32 multiplier = CVarGetInteger("gEnhancements.Graphics.IncreaseActorDrawDistance", 1);
    multiplier = MAX(multiplier, 1);
    if (multiplier > 1) {
        actor->projectedPos.z /= multiplier;
    }
}

// Enables Extended Culling options on specific actors by applying an inverse ratio of the widescreen aspect ratio
// to the projected X value of the actor. This tricks distance checks without having to replace hardcoded values.
// Requires that Ship_ExtendedCullingActorRestoreProjectedPos is called within the same function scope.
extern "C" void Ship_ExtendedCullingActorAdjustProjectedX(Actor* actor) {
    if (CVarGetInteger("gEnhancements.Graphics.ActorCullingAccountsForWidescreen", 0)) {
        f32 ratioAdjusted = Ship_GetExtendedAspectRatioMultiplier();
        actor->projectedPos.x /= ratioAdjusted;
    }
}

// Restores the projectedPos values on the actor after modifications from the Extended Culling hacks
extern "C" void Ship_ExtendedCullingActorRestoreProjectedPos(PlayState* play, Actor* actor) {
    f32 invW = 0.0f;
    Actor_GetProjectedPos(play, &actor->world.pos, &actor->projectedPos, &invW);
}

extern "C" const char* Ship_GetSceneName(s16 sceneId) {
    if (sceneNames.contains(sceneId)) {
        return sceneNames[sceneId];
    }

    return "Unknown";
}

ImVec4 Ship_GetItemColorTint(uint32_t itemId) {
    auto findColor = itemColorMap.find(itemId);
    return findColor != itemColorMap.end() ? findColor->second : ImVec4(1, 1, 1, 1);
}

std::string convertEnumToReadableName(const std::string& input) {
    std::string content = input;
    const std::string prefix = "RC_";
    if (content.rfind(prefix, 0) == 0) {
        content = content.substr(prefix.size());
    }

    std::vector<std::string> words;
    std::string word;
    std::istringstream stream(content);
    while (std::getline(stream, word, '_')) {
        std::transform(word.begin(), word.end(), word.begin(), [](unsigned char c) { return std::tolower(c); });
        if (word.empty()) {
            continue;
        }
        if (word == "hp") {
            word = "HP";
        } else {
            word[0] = std::toupper(word[0]);
        }
        words.push_back(word);
    }

    std::string result;
    for (size_t i = 0; i < words.size(); i++) {
        result += words[i];
        if (i + 1 < words.size()) {
            result += " ";
        }
    }
    return result;
}

std::string Ship_RemoveSpecialCharacters(const std::string& str) {
    std::string result;
    for (char ch : str) {
        if (std::isalnum(static_cast<unsigned char>(ch))) {
            result += ch;
        }
    }
    return result;
}

uint32_t Ship_Hash(std::string str) {
    uint32_t hash = 0x811c9dc5;
    for (char ch : str) {
        hash ^= static_cast<uint32_t>(ch);
        hash *= 0x01000193;
    }
    return hash;
}

extern "C" bool Ship_IsCStringEmpty(const char* str) {
    return str == NULL || str[0] == '\0';
}

// Build vertex coordinates for a quad command
// In order of top left, top right, bottom left, then bottom right
// Supports flipping the texture horizontally
extern "C" void Ship_CreateQuadVertexGroup(Vtx* vtxList, s32 xStart, s32 yStart, s32 width, s32 height, u8 flippedH) {
    vtxList[0].v.ob[0] = xStart;
    vtxList[0].v.ob[1] = yStart;
    vtxList[0].v.tc[0] = (flippedH ? width : 0) << 5;
    vtxList[0].v.tc[1] = 0 << 5;

    vtxList[1].v.ob[0] = xStart + width;
    vtxList[1].v.ob[1] = yStart;
    vtxList[1].v.tc[0] = (flippedH ? width * 2 : width) << 5;
    vtxList[1].v.tc[1] = 0 << 5;

    vtxList[2].v.ob[0] = xStart;
    vtxList[2].v.ob[1] = yStart + height;
    vtxList[2].v.tc[0] = (flippedH ? width : 0) << 5;
    vtxList[2].v.tc[1] = height << 5;

    vtxList[3].v.ob[0] = xStart + width;
    vtxList[3].v.ob[1] = yStart + height;
    vtxList[3].v.tc[0] = (flippedH ? width * 2 : width) << 5;
    vtxList[3].v.tc[1] = height << 5;
}

extern "C" f32 Ship_GetCharFontWidthNES(u8 character) {
    u8 adjustedChar = character - ' ';

    if (adjustedChar >= ARRAY_COUNTU(sNESFontWidths)) {
        return 0.0f;
    }

    return sNESFontWidths[adjustedChar];
}

extern "C" TexturePtr Ship_GetCharFontTextureNES(u8 character) {
    u8 adjustedChar = character - ' ';

    if (adjustedChar >= ARRAY_COUNTU(sNESFontWidths)) {
        return (TexturePtr)gEmptyTexture;
    }

    return (TexturePtr)fontTbl[adjustedChar];
}

static bool sRandomSeeded = false;
static uint64_t sRandomState = 0;
static const uint64_t sRandomMultiplier = 6364136223846793005ULL;
static const uint64_t sRandomIncrement = 11634580027462260723ULL;

extern "C" void Ship_Random_Seed(u64 seed) {
    sRandomSeeded = true;
    sRandomState = seed;
}

static uint32_t Ship_RandomNext32() {
    if (!sRandomSeeded) {
        Ship_Random_Seed(static_cast<uint64_t>(std::random_device{}()));
    }

    sRandomState = sRandomState * sRandomMultiplier + sRandomIncrement;
    uint32_t xorshifted = static_cast<uint32_t>(((sRandomState >> 18) ^ sRandomState) >> 27);
    uint32_t rot = static_cast<uint32_t>(sRandomState >> 59);
    return std::rotr(xorshifted, rot);
}

extern "C" s32 Ship_Random(s32 min, s32 max) {
    if (min == max) {
        return min;
    }

    assert(max > min);
    uint32_t n = max - min;
    uint32_t cutoff = UINT32_MAX - UINT32_MAX % n;

    for (;;) {
        uint32_t r = Ship_RandomNext32();
        if (r <= cutoff) {
            return min + r % n;
        }
    }
}

void LoadGuiTextures() {
    for (TexturePtr entry : gItemIcons) {
        const char* path = static_cast<const char*>(entry);
        Ship::Context::GetInstance()->GetWindow()->GetGui()->LoadGuiTexture(path, path, ImVec4(1, 1, 1, 1));
    }
    for (TexturePtr entry : gQuestIcons) {
        const char* path = static_cast<const char*>(entry);
        Ship::Context::GetInstance()->GetWindow()->GetGui()->LoadGuiTexture(path, path, ImVec4(1, 1, 1, 1));
    }
    for (TexturePtr entry : gBombersNotebookPhotos) {
        const char* path = static_cast<const char*>(entry);
        Ship::Context::GetInstance()->GetWindow()->GetGui()->LoadGuiTexture(path, path, ImVec4(1, 1, 1, 1));
    }
    for (const auto entry : miscellaneousTextures) {
        Ship::Context::GetInstance()->GetWindow()->GetGui()->LoadGuiTexture(entry, entry, ImVec4(1, 1, 1, 1));
    }
    for (const auto entry : digitList) {
        Ship::Context::GetInstance()->GetWindow()->GetGui()->LoadGuiTexture(entry, entry, ImVec4(1, 1, 1, 1));
    }
}
