#include "CommandMailbox.h"
#include "DisplayRefresh.h"

#include <atomic>
#include <cstring>

#include <libultraship/libultraship.h>
#include <libultraship/bridge/consolevariablebridge.h>
#include <fast/Fast3dWindow.h>

extern "C" {
#include "z64play.h"
#include "functions.h"

extern PlayState* gPlayState;
}

namespace {

struct TdsCommand {
    int32_t op;
    int32_t a;
    int32_t b;
    char name[TDS_CMD_NAME_CAPACITY];
};

TdsCommand sSlots[TDS_CMD_QUEUE_CAPACITY];
// head: consumer's next read index. tail: producer's next write index.
// Equal means empty; tail one lap ahead of head means full.
std::atomic<uint32_t> sHead{ 0 };
std::atomic<uint32_t> sTail{ 0 };

void Apply(const TdsCommand& cmd) {
    switch (cmd.op) {
        case TDS_CMD_PAUSE_SET: {
            // gPlayState is only ever mutated on this thread, so the guard
            // and the write cannot race (same argument as the publisher).
            PlayState* play = gPlayState;
            if (play != NULL) {
                play->frameAdvCtx.enabled = (cmd.a != 0);
            }
            break;
        }
        case TDS_CMD_CVAR_SET_INT:
            CVarSetInteger(cmd.name, cmd.a);
            break;
        case TDS_CMD_CVAR_SAVE:
            CVarSave();
            break;
        case TDS_CMD_SET_INTERNAL_RES: {
            const float multiplier = cmd.a / 100.0f;
            CVarSetFloat(CVAR_INTERNAL_RESOLUTION, multiplier);
            Ship::Context::GetInstance()->GetWindow()->SetResolutionMultiplier(multiplier);
            break;
        }
        case TDS_CMD_SET_MSAA:
            CVarSetInteger(CVAR_MSAA_VALUE, cmd.a);
            Ship::Context::GetInstance()->GetWindow()->SetMsaaLevel(cmd.a);
            break;
        case TDS_CMD_SET_TEXTURE_FILTER: {
            // SetTextureFilter is the one apply call not on the abstract Ship::Window
            // (engine/include/fast/Fast3dWindow.h:54), so it needs the concrete
            // backend. This is the only place in TerminaDS that knows which renderer
            // is in use; keep it here.
            auto window = std::dynamic_pointer_cast<Fast::Fast3dWindow>(
                Ship::Context::GetInstance()->GetWindow());
            if (window == nullptr) {
                // Non-Fast3D backend: drop the whole command rather than persist a
                // value the renderer will never honour. The snapshot publishes the
                // CVar, so a write here would show the UI a setting that is not live.
                break;
            }
            CVarSetInteger(CVAR_TEXTURE_FILTER, cmd.a);
            window->SetTextureFilter((Fast::FilteringMode)cmd.a);
            break;
        }
        case TDS_CMD_SET_DISPLAY_HZ:
            TerminaDS_SetDisplayRefreshHz(cmd.a);
            break;
        default:
            // Validated at submit; an unknown op here is a torn build --
            // drop it rather than guess.
            break;
    }
}

} // namespace

extern "C" int32_t TerminaDS_SubmitCommand(int32_t op, int32_t a, int32_t b, const char* name) {
    if (op < TDS_CMD_PAUSE_SET || op > TDS_CMD_SET_DISPLAY_HZ) {
        return TDS_SUBMIT_INVALID;
    }
    const bool needsName = (op == TDS_CMD_CVAR_SET_INT);
    if (needsName && (name == NULL || std::strlen(name) >= TDS_CMD_NAME_CAPACITY)) {
        return TDS_SUBMIT_INVALID;
    }
    // Range-check the semantic opcodes at submit, not at apply. An
    // out-of-range value reaching the engine would be a real fault; rejecting
    // it here means the caller sees it as a status rather than the game
    // silently taking a nonsense multiplier.
    if ((op == TDS_CMD_SET_INTERNAL_RES && (a < 50 || a > 200)) ||
        (op == TDS_CMD_SET_MSAA && (a < 1 || a > 8)) ||
        (op == TDS_CMD_SET_TEXTURE_FILTER && (a < 0 || a > 2)) ||
        (op == TDS_CMD_SET_DISPLAY_HZ && (a < 1 || a > 1000))) {
        return TDS_SUBMIT_INVALID;
    }

    const uint32_t tail = sTail.load(std::memory_order_relaxed);
    const uint32_t head = sHead.load(std::memory_order_acquire);
    if (tail - head >= TDS_CMD_QUEUE_CAPACITY) {
        return TDS_SUBMIT_FULL;
    }

    TdsCommand& slot = sSlots[tail % TDS_CMD_QUEUE_CAPACITY];
    slot.op = op;
    slot.a = a;
    slot.b = b;
    if (needsName) {
        std::strncpy(slot.name, name, TDS_CMD_NAME_CAPACITY - 1);
        slot.name[TDS_CMD_NAME_CAPACITY - 1] = '\0';
    } else {
        slot.name[0] = '\0';
    }

    // Release publishes the slot contents before the new tail is visible.
    sTail.store(tail + 1, std::memory_order_release);
    return TDS_SUBMIT_OK;
}

extern "C" void TerminaDS_DrainCommands(void) {
    uint32_t head = sHead.load(std::memory_order_relaxed);
    const uint32_t tail = sTail.load(std::memory_order_acquire);

    while (head != tail) {
        Apply(sSlots[head % TDS_CMD_QUEUE_CAPACITY]);
        head++;
    }
    // Release lets the producer reuse the consumed slots.
    sHead.store(head, std::memory_order_release);
}
