/*
 * Termina DS: the top-screen PAUSED veil (design handoff section 2).
 *
 * An always-on-top, input-transparent ImGui foreground overlay drawn while the
 * engine's frame-advance gate holds the Play update frozen. Rendering continues
 * while frozen -- only the update body is gated (z_play.c:988) -- which is
 * exactly why this overlay is possible at all.
 *
 * Registered like Notification::Window (mm/2s2h/BenGui/BenGui.cpp:187-189).
 *
 * KNOWN FIDELITY GAP: the design specifies
 * backdrop-filter: blur(7px) saturate(.42) brightness(.34). An ImGui window has
 * no framebuffer or shader access, so the frozen render is dimmed and tinted by
 * layered draw-list rects rather than blurred. Accepted; see the spec's
 * section 10.
 *
 * This file does NOT touch game state beyond the one frame-advance read it
 * needs to know whether to draw, which is the same value SnapshotPublisher.cpp
 * already publishes.
 */
#include "CinzelFontData.h"

#include <memory>

#include <imgui.h>
#include <libultraship/libultraship.h>

#include "2s2h/ShipInit.hpp"

extern "C" {
#include "z64play.h"

extern PlayState* gPlayState;
}

namespace {

ImFont* sVeilFont = nullptr;

/* Design px at 1920x1080; the veil scales with the viewport's shorter axis. */
constexpr float kWordmarkPx = 176.0f;

bool VeilShouldDraw() {
    const PlayState* play = gPlayState;
    return play != NULL && play->frameAdvCtx.enabled;
}

class PauseVeilWindow : public Ship::GuiWindow {
  public:
    using GuiWindow::GuiWindow;

    void InitElement() override {
    }

    void UpdateElement() override {
    }

    void DrawElement() override {
    }

    void Draw() override {
        if (!VeilShouldDraw()) {
            return;
        }

        ImGuiViewport* vp = ImGui::GetMainViewport();
        // The foreground draw list is emitted after every window's draw list,
        // so the veil cannot be covered by the game. This matters because the
        // game render is itself an ImGui window ("Main Game", Gui.cpp:732-736)
        // that blits an opaque framebuffer over the whole viewport; an ordinary
        // window with NoBringToFrontOnFocus is push_front'd (imgui.cpp:6583)
        // and therefore drawn UNDERNEATH it.
        ImDrawList* draw = ImGui::GetForegroundDrawList(vp);
        const ImVec2 origin = vp->Pos;
        const ImVec2 size = vp->Size;
        const float scale = size.y / 1080.0f;

        /*
         * The design's veil is a dark radial over the render. Approximated
         * with two stacked rects: a flat dim plus a vertical gradient that is
         * darkest at the edges, which reads as the intended vignette without a
         * shader.
         */
        draw->AddRectFilled(origin, ImVec2(origin.x + size.x, origin.y + size.y), IM_COL32(0, 0, 0, 190));
        draw->AddRectFilledMultiColor(origin, ImVec2(origin.x + size.x, origin.y + size.y),
                                      IM_COL32(26, 14, 44, 90), IM_COL32(26, 14, 44, 90),
                                      IM_COL32(0, 0, 0, 150), IM_COL32(0, 0, 0, 150));

        const float centerX = origin.x + size.x * 0.5f;
        float y = origin.y + size.y * 0.32f;

        /* Wordmark. */
        ImFont* wordmarkFont = sVeilFont != nullptr ? sVeilFont : ImGui::GetFont();
        const float wordmarkSize = kWordmarkPx * scale;
        const char* kWordmark = "PAUSED";
        /*
         * Tracking is .18em in the design; ImGui has no letter-spacing, so the
         * glyphs are drawn one at a time with the gap added manually.
         */
        const float tracking = wordmarkSize * 0.18f;
        float wordWidth = 0.0f;
        for (const char* c = kWordmark; *c != '\0'; ++c) {
            wordWidth += wordmarkFont->CalcTextSizeA(wordmarkSize, FLT_MAX, 0.0f, c, c + 1).x;
            wordWidth += tracking;
        }
        wordWidth -= tracking;

        float penX = centerX - wordWidth * 0.5f;
        for (const char* c = kWordmark; *c != '\0'; ++c) {
            draw->AddText(wordmarkFont, wordmarkSize, ImVec2(penX, y), IM_COL32(246, 236, 255, 255), c, c + 1);
            penX += wordmarkFont->CalcTextSizeA(wordmarkSize, FLT_MAX, 0.0f, c, c + 1).x + tracking;
        }
        y += wordmarkSize * 1.05f;

        ImFont* textFont = ImGui::GetFont();

        /* Subtitle. */
        DrawCenteredText(draw, textFont, centerX, y, 21.0f * scale, IM_COL32(157, 141, 190, 255),
                         "THE CLOCK HOLDS ITS BREATH", 9.0f * scale);
        y += 40.0f * scale;

        /* Rule. */
        draw->AddLine(ImVec2(centerX - 210.0f * scale, y), ImVec2(centerX + 210.0f * scale, y),
                      IM_COL32(180, 140, 232, 140), 1.0f);
        y += 34.0f * scale;

        /* Hint. */
        DrawCenteredText(draw, textFont, centerX, y, 22.0f * scale, IM_COL32(201, 191, 224, 255),
                         "CONTINUE ON THE BOTTOM SCREEN", 2.5f * scale);
    }

  private:
    static void DrawCenteredText(ImDrawList* draw, ImFont* font, float centerX, float y, float pixelSize,
                                 ImU32 color, const char* text, float tracking) {
        float width = 0.0f;
        for (const char* c = text; *c != '\0'; ++c) {
            width += font->CalcTextSizeA(pixelSize, FLT_MAX, 0.0f, c, c + 1).x + tracking;
        }
        width -= tracking;

        float penX = centerX - width * 0.5f;
        for (const char* c = text; *c != '\0'; ++c) {
            draw->AddText(font, pixelSize, ImVec2(penX, y), color, c, c + 1);
            penX += font->CalcTextSizeA(pixelSize, FLT_MAX, 0.0f, c, c + 1).x + tracking;
        }
    }
};

std::shared_ptr<PauseVeilWindow> sVeilWindow;

static RegisterShipInitFunc sRegisterPauseVeil([]() {
    // Same re-registration guard as SnapshotPublisher.cpp:212-226:
    // PresetManager.cpp:374 calls ShipInit::Init("*") on every preset load,
    // and a second registration would draw the veil twice per frame.
    static bool registered = false;
    if (registered) {
        return;
    }
    registered = true;

    auto gui = Ship::Context::GetInstance()->GetWindow()->GetGui();
    sVeilWindow = std::make_shared<PauseVeilWindow>("gWindows.TerminaDSPauseVeil", "Pause Veil");
    gui->AddGuiWindow(sVeilWindow);
    sVeilWindow->Show();
});

} // namespace

extern "C" void TerminaDS_LoadVeilFont(void) {
    ImGuiIO& io = ImGui::GetIO();
    ImFontConfig config;
    config.MergeMode = false;
    sVeilFont =
        io.Fonts->AddFontFromMemoryCompressedBase85TTF(kCinzelCompressedBase85, kWordmarkPx, &config);
}
