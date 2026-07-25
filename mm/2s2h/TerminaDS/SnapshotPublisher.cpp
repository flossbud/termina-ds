/*
 * Termina DS: game-thread half of the Phase 2 read-only state bridge.
 *
 * SnapshotPublisher.cpp (reads) and CommandMailbox.cpp (writes) are the only
 * files in the project that touch game state, both exclusively on the game
 * thread. That containment is deliberate -- gPlayState is NULL across every
 * scene transition (z_play.c:481) and the player actor can be absent while a
 * PlayState exists, so these accesses are only safe on the thread that owns
 * those lifetimes. Everything outside the pair exchanges plain command and
 * snapshot data.
 *
 * Registration uses RegisterShipInitFunc, so no inherited file is edited.
 */
#include "CommandMailbox.h"
#include "GameSnapshot.h"

#include <atomic>
#include <cstring>

#include "2s2h/BenGui/BenGui.hpp"
#include "2s2h/GameInteractor/GameInteractor.h"
#include "2s2h/ShipInit.hpp"

extern "C" {
#include "z64save.h"
#include "z64play.h"
#include "z64interface.h"
#include "macros.h"
#include "variables.h"

extern SaveContext gSaveContext;
extern PlayState* gPlayState;
}

#ifdef __ANDROID__
#include <android/log.h>
#endif

namespace {

// Seqlock: odd while a write is in progress, even when the values are stable.
std::atomic<uint32_t> sSeq{ 0 };

// Relaxed atomics rather than plain int32_t. A seqlock over non-atomic data is
// formally a data race under the C++ memory model even though it works in
// practice; on arm64 relaxed atomics compile to ordinary loads and stores, so
// this is free at runtime and makes the code defined rather than lucky.
//
// Slot 0 is seeded with the schema version because that is a property of this
// binary, not of any one frame: a read landing before the first publish must
// decode cleanly and report FRAME_COUNTER == 0, not a bogus mismatch against
// version 0. `constinit` enforces that this stays constant-initialised -- if a
// later edit made the initializer dynamic, this would fail to compile instead
// of silently reintroducing a window where an early reader could observe slot
// 0 unseeded.
static_assert(TDS_SNAP_IDX_SCHEMA_VERSION == 0, "slot 0 must be the schema version");
constinit std::atomic<int32_t> sValues[TDS_SNAP_COUNT] = { TDS_SNAP_SCHEMA_VERSION };

// Touched only by the game thread inside Publish().
uint32_t sFrameCounter = 0;
#ifdef __ANDROID__
bool sLoggedFirstPublish = false;
#endif

int32_t FloatBits(float value) {
    int32_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    return bits;
}

// Resolve a button item's ammo without trusting the item id.
//
// AMMO(item) expands to inventory.ammo[gItemSlots[item]], indexing two arrays
// in a row with no bounds check. An empty button holds ITEM_NONE (0xFF), which
// is past the end of gItemSlots, and plenty of valid items map to a slot that
// is past the end of ammo[]. Both are out-of-bounds reads on the game thread.
int32_t ResolveAmmo(uint8_t item) {
    if (item >= ARRAY_COUNT(gItemSlots)) {
        return 0;
    }
    uint8_t slot = gItemSlots[item];
    if (slot >= ARRAY_COUNT(gSaveContext.save.saveInfo.inventory.ammo)) {
        return 0;
    }
    return gSaveContext.save.saveInfo.inventory.ammo[slot];
}

void Publish() {
    int32_t v[TDS_SNAP_COUNT] = { 0 };
    int32_t flags = 0;

    v[TDS_SNAP_IDX_SCHEMA_VERSION] = TDS_SNAP_SCHEMA_VERSION;
    v[TDS_SNAP_IDX_FRAME_COUNTER] = static_cast<int32_t>(++sFrameCounter);

    // gSaveContext is a static struct and is always addressable, including on
    // the title screen and file select.
    const SavePlayerData& playerData = gSaveContext.save.saveInfo.playerData;
    v[TDS_SNAP_IDX_HEALTH] = playerData.health;
    v[TDS_SNAP_IDX_HEALTH_CAPACITY] = playerData.healthCapacity;
    v[TDS_SNAP_IDX_MAGIC] = playerData.magic;
    v[TDS_SNAP_IDX_MAGIC_CAPACITY] = gSaveContext.magicCapacity;
    v[TDS_SNAP_IDX_MAGIC_LEVEL] = playerData.magicLevel;
    v[TDS_SNAP_IDX_RUPEES] = playerData.rupees;
    if (playerData.doubleDefense) {
        flags |= TDS_SNAP_FLAG_DOUBLE_DEFENSE;
    }
    // v2: fileNum is the committed save slot; the title screen parks it at
    // 0xFF (z_title.c:283). Unsigned compare rejects the sentinel and any
    // debug negative in one test.
    if ((uint32_t)gSaveContext.fileNum <= 2u) {
        flags |= TDS_SNAP_FLAG_SAVE_LOADED;
    }
    if (BenGui::IsBenMenuVisible()) {
        flags |= TDS_SNAP_FLAG_MENU_OPEN;
    }

    v[TDS_SNAP_IDX_PLAYER_FORM] = gSaveContext.save.playerForm;
    v[TDS_SNAP_IDX_EQUIPPED_MASK] = gSaveContext.save.equippedMask;
    v[TDS_SNAP_IDX_DAY] = gSaveContext.save.day;
    v[TDS_SNAP_IDX_TIME_OF_DAY] = gSaveContext.save.time;
    if (gSaveContext.save.isNight) {
        flags |= TDS_SNAP_FLAG_IS_NIGHT;
    }

    // GET_CUR_FORM_BTN_ITEM already encodes MM's rule that B is per-form while
    // the C buttons are shared across forms. Use it rather than indexing
    // buttonItems[form][n] by hand and reimplementing that rule wrongly.
    static const int32_t kSlots[] = { EQUIP_SLOT_B, EQUIP_SLOT_C_LEFT, EQUIP_SLOT_C_DOWN, EQUIP_SLOT_C_RIGHT };
    // The loop below writes consecutive slots in kSlots order, so both runs
    // have to stay contiguous if the layout enum is ever reordered.
    static_assert(TDS_SNAP_IDX_BTN_ITEM_C_RIGHT == TDS_SNAP_IDX_BTN_ITEM_B + 3, "button item slots must be contiguous");
    static_assert(TDS_SNAP_IDX_BTN_AMMO_C_RIGHT == TDS_SNAP_IDX_BTN_AMMO_B + 3, "button ammo slots must be contiguous");
    for (int32_t i = 0; i < ARRAY_COUNT(kSlots); i++) {
        uint8_t item = GET_CUR_FORM_BTN_ITEM(kSlots[i]);
        v[TDS_SNAP_IDX_BTN_ITEM_B + i] = item;
        v[TDS_SNAP_IDX_BTN_AMMO_B + i] = ResolveAmmo(item);
    }

    // Everything below here is pointer-guarded. When a guard fails the
    // corresponding slots stay zero -- never stale -- so the UI cannot present
    // a previous scene's position as current.
    const PlayState* play = gPlayState;
    if (play != NULL) {
        flags |= TDS_SNAP_FLAG_PLAY_STATE_VALID;
        v[TDS_SNAP_IDX_SCENE_ID] = play->sceneId;
        v[TDS_SNAP_IDX_ROOM_NUM] = play->roomCtx.curRoom.num;
        v[TDS_SNAP_IDX_PAUSE_STATE] = FrameAdvance_IsEnabled((PlayState*)play);
        if (play->pauseCtx.state != PAUSE_STATE_OFF) {
            flags |= TDS_SNAP_FLAG_MENU_OPEN;
        }

        const Player* player = GET_PLAYER(play);
        if (player != NULL) {
            flags |= TDS_SNAP_FLAG_PLAYER_VALID;
            v[TDS_SNAP_IDX_PLAYER_X] = FloatBits(player->actor.world.pos.x);
            v[TDS_SNAP_IDX_PLAYER_Y] = FloatBits(player->actor.world.pos.y);
            v[TDS_SNAP_IDX_PLAYER_Z] = FloatBits(player->actor.world.pos.z);
            v[TDS_SNAP_IDX_PLAYER_YAW] = player->actor.shape.rot.y;
        }
    }

    v[TDS_SNAP_IDX_FLAGS] = flags;

    // Seqlock write. The release fence keeps the value stores from being
    // hoisted above the odd sequence number; the release store publishes them.
    uint32_t seq = sSeq.load(std::memory_order_relaxed);
    sSeq.store(seq + 1, std::memory_order_relaxed);
    std::atomic_thread_fence(std::memory_order_release);

    for (int32_t i = 0; i < TDS_SNAP_COUNT; i++) {
        sValues[i].store(v[i], std::memory_order_relaxed);
    }

    sSeq.store(seq + 2, std::memory_order_release);

#ifdef __ANDROID__
    if (!sLoggedFirstPublish) {
        sLoggedFirstPublish = true;
        // Both Thor displays are FLAG_SECURE, so the bottom screen cannot be
        // screenshotted. This line is how logcat proves the static
        // registration ran without needing the user to look at the device.
        __android_log_print(ANDROID_LOG_INFO, "TerminaDS",
                            "Snapshot: publisher registered, first publish (schema %d, %d slots)",
                            TDS_SNAP_SCHEMA_VERSION, TDS_SNAP_COUNT);
    }
#endif
}

static RegisterShipInitFunc sRegisterSnapshotPublisher([]() {
    // ShipInit::Init("*") is NOT called only once at startup. Besides
    // ShipInit::InitAll() from BenPort.cpp:725, PresetManager.cpp:374 calls it
    // again on every preset load. Without this guard each preset load would add
    // another Publish handler to the OnGameStateUpdate hook list, so Publish
    // would run N times per engine frame and sFrameCounter would advance by N.
    // FRAME_COUNTER is the diagnostic that separates "the bridge is broken"
    // from "the game loop stopped"; N frames per frame silently destroys it,
    // and on a FLAG_SECURE screen there is no second opinion. Do not delete
    // this as redundant -- same idiom, same reason, as PlayAsKafei.cpp:71-78.
    static bool registered = false;
    if (registered) {
        return;
    }
    registered = true;

    GameInteractor::Instance->RegisterGameHook<GameInteractor::OnGameStateUpdate>([]() {
        // Drain before publishing: the same frame's snapshot reports the
        // commands' effects, closing the observe-don't-assume loop.
        TerminaDS_DrainCommands();
        Publish();
    });
});

} // namespace

extern "C" int32_t TerminaDS_ReadSnapshot(int32_t* out, int count) {
    // A short buffer is a build skew, not a transient condition: it fails every
    // call forever. It gets its own status so the caller cannot mistake it for
    // a seqlock collision and sit there retrying.
    if (out == NULL || count < TDS_SNAP_COUNT) {
        return TDS_SNAP_STATUS_BUFFER_TOO_SMALL;
    }

    // Four attempts is generous: the writer holds the array for well under a
    // microsecond, once every 16.6 ms.
    for (int attempt = 0; attempt < 4; attempt++) {
        uint32_t before = sSeq.load(std::memory_order_acquire);
        if ((before & 1u) != 0u) {
            continue; // a write is in progress
        }

        for (int32_t i = 0; i < TDS_SNAP_COUNT; i++) {
            out[i] = sValues[i].load(std::memory_order_relaxed);
        }

        std::atomic_thread_fence(std::memory_order_acquire);
        if (sSeq.load(std::memory_order_relaxed) == before) {
            return TDS_SNAP_STATUS_OK;
        }
    }

    return TDS_SNAP_STATUS_RETRY_EXHAUSTED;
}
