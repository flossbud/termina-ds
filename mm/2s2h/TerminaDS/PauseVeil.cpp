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

#include <cfloat>
#include <cmath>
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
constexpr float kPi = 3.14159265358979323846f;

struct Rgb {
    float r;
    float g;
    float b;
};

bool VeilShouldDraw() {
    const PlayState* play = gPlayState;
    return play != NULL && play->frameAdvCtx.enabled;
}

float Clamp01(float value) {
    if (value < 0.0f) {
        return 0.0f;
    }
    if (value > 1.0f) {
        return 1.0f;
    }
    return value;
}

float Lerp(float from, float to, float progress) {
    return from + (to - from) * Clamp01(progress);
}

float CubicBezierCoordinate(float parameter, float control1, float control2) {
    const float inverse = 1.0f - parameter;
    return 3.0f * inverse * inverse * parameter * control1 +
           3.0f * inverse * parameter * parameter * control2 + parameter * parameter * parameter;
}

/*
 * CSS cubic-bezier timing functions map time through the curve's x component.
 * Bisection is deterministic and keeps both the input and output in [0, 1].
 */
float CubicBezierEase(float progress, float x1, float y1, float x2, float y2) {
    const float clamped = Clamp01(progress);
    if (clamped == 0.0f || clamped == 1.0f) {
        return clamped;
    }
    float low = 0.0f;
    float high = 1.0f;
    for (int i = 0; i < 16; ++i) {
        const float parameter = (low + high) * 0.5f;
        if (CubicBezierCoordinate(parameter, x1, x2) < clamped) {
            low = parameter;
        } else {
            high = parameter;
        }
    }
    return Clamp01(CubicBezierCoordinate((low + high) * 0.5f, y1, y2));
}

float CssEase(float progress) {
    return CubicBezierEase(progress, 0.25f, 0.1f, 0.25f, 1.0f);
}

float CssEaseInOut(float progress) {
    return CubicBezierEase(progress, 0.42f, 0.0f, 0.58f, 1.0f);
}

float EntranceProgress(double elapsed, float delay, float duration) {
    return Clamp01(static_cast<float>((elapsed - delay) / duration));
}

float LoopProgress(double now, float duration) {
    return Clamp01(static_cast<float>(std::fmod(now, static_cast<double>(duration)) / duration));
}

float LoopPulse(double now, float duration) {
    const float phase = LoopProgress(now, duration);
    const float leg = phase <= 0.5f ? phase * 2.0f : (1.0f - phase) * 2.0f;
    return CssEaseInOut(Clamp01(leg));
}

ImU32 ColorWithAlpha(int red, int green, int blue, float alpha) {
    const int byteAlpha = static_cast<int>(Clamp01(alpha) * 255.0f + 0.5f);
    return IM_COL32(red, green, blue, byteAlpha);
}

float TrackedTextWidth(ImFont* font, float pixelSize, const char* text, float tracking) {
    float width = 0.0f;
    for (const char* c = text; *c != '\0'; ++c) {
        width += font->CalcTextSizeA(pixelSize, FLT_MAX, 0.0f, c, c + 1).x + tracking;
    }
    if (*text != '\0') {
        width -= tracking;
    }
    return width;
}

void DrawTrackedText(ImDrawList* draw, ImFont* font, float x, float y, float pixelSize, ImU32 color, const char* text,
                     float tracking) {
    float penX = x;
    for (const char* c = text; *c != '\0'; ++c) {
        draw->AddText(font, pixelSize, ImVec2(penX, y), color, c, c + 1);
        penX += font->CalcTextSizeA(pixelSize, FLT_MAX, 0.0f, c, c + 1).x + tracking;
    }
}

Rgb LerpColor(const Rgb& from, const Rgb& to, float progress) {
    return { Lerp(from.r, to.r, progress), Lerp(from.g, to.g, progress), Lerp(from.b, to.b, progress) };
}

Rgb SampleShimmer(float glyphPosition, float sweepProgress) {
    struct ColorStop {
        float position;
        Rgb color;
    };
    static constexpr ColorStop kStops[] = {
        { 0.00f, { 91.0f, 63.0f, 146.0f } },  { 0.24f, { 143.0f, 104.0f, 201.0f } },
        { 0.46f, { 246.0f, 236.0f, 255.0f } }, { 0.58f, { 180.0f, 140.0f, 232.0f } },
        { 0.92f, { 91.0f, 63.0f, 146.0f } },
    };

    /*
     * pzSweep uses gradient-clipped text, which ImGui cannot express. Sample
     * the approved shimmer palette once per glyph around a highlight travelling
     * from -70% to 170% instead; the position and 5.2s linear timing are exact,
     * while the six discrete colour samples approximate the clipped gradient.
     */
    const float sheenCenter = Lerp(-0.70f, 1.70f, sweepProgress);
    const float gradientPosition = 0.46f + (glyphPosition - sheenCenter) / 0.52f;
    if (gradientPosition <= kStops[0].position || gradientPosition >= kStops[4].position) {
        return kStops[0].color;
    }
    for (int i = 0; i < 4; ++i) {
        if (gradientPosition <= kStops[i + 1].position) {
            const float span = kStops[i + 1].position - kStops[i].position;
            const float progress = Clamp01((gradientPosition - kStops[i].position) / span);
            return LerpColor(kStops[i].color, kStops[i + 1].color, progress);
        }
    }
    return kStops[0].color;
}

class PauseVeilWindow : public Ship::GuiWindow {
  public:
    using GuiWindow::GuiWindow;

    void InitElement() override {
    }

    void UpdateElement() override {
        const bool shouldDraw = VeilShouldDraw();
        if (!shouldDraw) {
            mWasDrawing = false;
            mShownAt = 0.0;
            return;
        }
        if (!mWasDrawing) {
            mShownAt = ImGui::GetTime();
            mWasDrawing = true;
        }
    }

    void DrawElement() override {
    }

    void Draw() override {
        if (!VeilShouldDraw()) {
            return;
        }

        const double now = ImGui::GetTime();
        const double elapsed = now >= mShownAt ? now - mShownAt : 0.0;
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
        ImFont* wordmarkFont = sVeilFont != nullptr ? sVeilFont : ImGui::GetFont();
        ImFont* textFont = ImGui::GetFont();
        const float wordmarkSize = kWordmarkPx * scale;
        const float contentTop = origin.y + (size.y - 378.0f * scale) * 0.5f;

        /* Ornament and pzHand. */
        const float ornamentRise = CssEase(EntranceProgress(elapsed, 0.10f, 0.50f));
        const float ornamentY = contentTop + 22.0f * scale + (1.0f - ornamentRise) * 14.0f * scale;
        const float clockRadius = 22.0f * scale;
        const float ornamentGap = 22.0f * scale;
        const float ornamentRuleWidth = 120.0f * scale;
        const float ornamentRuleHalfHeight = 0.5f * scale;
        const ImU32 ornamentRuleClear = ColorWithAlpha(180, 140, 232, 0.0f);
        const ImU32 ornamentRuleBright = ColorWithAlpha(180, 140, 232, 0.70f * ornamentRise);
        const float leftRuleEnd = centerX - clockRadius - ornamentGap;
        const float rightRuleStart = centerX + clockRadius + ornamentGap;
        draw->AddRectFilledMultiColor(
            ImVec2(leftRuleEnd - ornamentRuleWidth, ornamentY - ornamentRuleHalfHeight),
            ImVec2(leftRuleEnd, ornamentY + ornamentRuleHalfHeight), ornamentRuleClear, ornamentRuleBright,
            ornamentRuleBright, ornamentRuleClear);
        draw->AddRectFilledMultiColor(
            ImVec2(rightRuleStart, ornamentY - ornamentRuleHalfHeight),
            ImVec2(rightRuleStart + ornamentRuleWidth, ornamentY + ornamentRuleHalfHeight), ornamentRuleBright,
            ornamentRuleClear, ornamentRuleClear, ornamentRuleBright);
        draw->AddCircle(ImVec2(centerX, ornamentY), clockRadius,
                        ColorWithAlpha(180, 140, 232, 0.55f * ornamentRise), 0, 1.5f * scale);

        const float handPulse = LoopPulse(now, 5.4f);
        const float handAngle = Lerp(26.6f, 29.4f, handPulse) * kPi / 180.0f;
        const float handLength = 15.0f * scale;
        const ImVec2 handEnd(centerX + std::sin(handAngle) * handLength,
                             ornamentY - std::cos(handAngle) * handLength);
        draw->AddLine(ImVec2(centerX, ornamentY), handEnd, ColorWithAlpha(203, 176, 242, ornamentRise),
                      2.0f * scale);
        draw->AddCircleFilled(ImVec2(centerX, ornamentY), 3.0f * scale,
                              ColorWithAlpha(203, 176, 242, ornamentRise));

        /* Wordmark: pzWordIn tracking/opacity, pzSweep, and pzGlow. */
        const char* kWordmark = "PAUSED";
        /*
         * ImGui has no text blur, so pzWordIn retains its exact .42em -> .18em
         * tracking and opacity entrance but omits only the 9px -> 0 blur.
         */
        const float wordIn =
            CubicBezierEase(EntranceProgress(elapsed, 0.06f, 0.80f), 0.20f, 0.80f, 0.20f, 1.0f);
        const float tracking = wordmarkSize * Lerp(0.42f, 0.18f, wordIn);
        const float wordWidth = TrackedTextWidth(wordmarkFont, wordmarkSize, kWordmark, tracking);
        const float wordmarkY = contentTop + 70.0f * scale;

        /*
         * pzGlow is a blurred CSS drop-shadow, unavailable on an ImDrawList.
         * Eight low-alpha offset copies approximate its pulse without a shader
         * or an excessive number of wordmark draws.
         */
        static constexpr float kGlowDirections[8][2] = {
            { -1.0f, 0.0f }, { 1.0f, 0.0f },  { 0.0f, -1.0f }, { 0.0f, 1.0f },
            { -0.7f, -0.7f }, { 0.7f, -0.7f }, { -0.7f, 0.7f }, { 0.7f, 0.7f },
        };
        const float glowPulse = LoopPulse(now, 4.4f);
        const float glowStrength = Lerp(0.30f, 0.62f, glowPulse);
        const float glowOffset = Lerp(1.5f, 4.5f, glowPulse) * scale;
        const ImU32 glowColor = ColorWithAlpha(180, 140, 232, wordIn * glowStrength * 0.18f);
        for (const auto& direction : kGlowDirections) {
            DrawTrackedText(draw, wordmarkFont, centerX - wordWidth * 0.5f + direction[0] * glowOffset,
                            wordmarkY + direction[1] * glowOffset, wordmarkSize, glowColor, kWordmark, tracking);
        }

        const float sweepProgress = LoopProgress(now, 5.2f);
        float penX = centerX - wordWidth * 0.5f;
        for (const char* c = kWordmark; *c != '\0'; ++c) {
            const float glyphWidth = wordmarkFont->CalcTextSizeA(wordmarkSize, FLT_MAX, 0.0f, c, c + 1).x;
            const float glyphPosition =
                Clamp01((penX + glyphWidth * 0.5f - (centerX - wordWidth * 0.5f)) / wordWidth);
            const Rgb color = SampleShimmer(glyphPosition, sweepProgress);
            draw->AddText(wordmarkFont, wordmarkSize, ImVec2(penX, wordmarkY),
                          ColorWithAlpha(static_cast<int>(color.r + 0.5f), static_cast<int>(color.g + 0.5f),
                                         static_cast<int>(color.b + 0.5f), wordIn),
                          c, c + 1);
            penX += glyphWidth + tracking;
        }

        /* pzRise: subtitle at .34s. */
        const float subtitleRise = CssEase(EntranceProgress(elapsed, 0.34f, 0.60f));
        const float subtitleY = wordmarkY + wordmarkSize * 0.95f + 14.0f * scale +
                                (1.0f - subtitleRise) * 14.0f * scale;
        DrawCenteredText(draw, textFont, centerX, subtitleY, 21.0f * scale,
                         ColorWithAlpha(157, 141, 190, subtitleRise), "THE CLOCK HOLDS ITS BREATH", 9.0f * scale);

        /* pzRule: grow the centered gradient hairline to 420px after .3s. */
        const float ruleIn =
            CubicBezierEase(EntranceProgress(elapsed, 0.30f, 0.90f), 0.20f, 0.80f, 0.20f, 1.0f);
        const float ruleHalfWidth = 210.0f * scale * ruleIn;
        const float ruleY = wordmarkY + wordmarkSize * 0.95f + 79.0f * scale;
        const float ruleHalfHeight = 0.5f * scale;
        const ImU32 ruleClear = ColorWithAlpha(180, 140, 232, 0.0f);
        const ImU32 ruleBright = ColorWithAlpha(180, 140, 232, 0.45f * ruleIn);
        draw->AddRectFilledMultiColor(ImVec2(centerX - ruleHalfWidth, ruleY - ruleHalfHeight),
                                      ImVec2(centerX, ruleY + ruleHalfHeight), ruleClear, ruleBright, ruleBright,
                                      ruleClear);
        draw->AddRectFilledMultiColor(ImVec2(centerX, ruleY - ruleHalfHeight),
                                      ImVec2(centerX + ruleHalfWidth, ruleY + ruleHalfHeight), ruleBright, ruleClear,
                                      ruleClear, ruleBright);

        /* pzRise at .5s plus the 2.1s pzBreathe selection diamond. */
        const char* kHint = "CONTINUE ON THE BOTTOM SCREEN";
        const float hintRise = CssEase(EntranceProgress(elapsed, 0.50f, 0.60f));
        const float hintSize = 22.0f * scale;
        const float hintTracking = 2.5f * scale;
        const float hintWidth = TrackedTextWidth(textFont, hintSize, kHint, hintTracking);
        const float diamondSize = 9.0f * scale;
        const float hintGap = 14.0f * scale;
        const float hintGroupWidth = diamondSize + hintGap + hintWidth;
        const float hintX = centerX - hintGroupWidth * 0.5f;
        const float hintY = ruleY + 35.0f * scale + (1.0f - hintRise) * 14.0f * scale;
        const ImVec2 diamondCenter(hintX + diamondSize * 0.5f, hintY + hintSize * 0.5f);
        const float diamondRadius = diamondSize * 0.5f;
        const float breatheOpacity = Lerp(0.40f, 1.0f, LoopPulse(now, 2.1f));
        const ImU32 diamondColor = ColorWithAlpha(180, 140, 232, hintRise * breatheOpacity);
        const ImVec2 diamondPoints[4] = {
            ImVec2(diamondCenter.x, diamondCenter.y - diamondRadius),
            ImVec2(diamondCenter.x + diamondRadius, diamondCenter.y),
            ImVec2(diamondCenter.x, diamondCenter.y + diamondRadius),
            ImVec2(diamondCenter.x - diamondRadius, diamondCenter.y),
        };
        draw->AddConvexPolyFilled(diamondPoints, 4, diamondColor);
        DrawTrackedText(draw, textFont, hintX + diamondSize + hintGap, hintY, hintSize,
                        ColorWithAlpha(201, 191, 224, hintRise), kHint, hintTracking);

        /*
         * The approved pzRise .2s stagger belongs to the two corner captions.
         * They are static design copy here because the veil intentionally reads
         * no game state beyond the frame-advance gate.
         */
        const float cornerRise = CssEase(EntranceProgress(elapsed, 0.20f, 0.60f));
        const float cornerY = origin.y + 30.0f * scale + (1.0f - cornerRise) * 14.0f * scale;
        const float cornerSize = 15.0f * scale;
        const ImU32 cornerColor = ColorWithAlpha(125, 111, 156, cornerRise);
        const float cornerLeft = origin.x + 34.0f * scale;
        const char* kDay = "DAY 1";
        const float dayTracking = 5.0f * scale;
        const float dayWidth = TrackedTextWidth(textFont, cornerSize, kDay, dayTracking);
        DrawTrackedText(draw, textFont, cornerLeft, cornerY, cornerSize, cornerColor, kDay, dayTracking);
        const float separatorX = cornerLeft + dayWidth + 12.0f * scale;
        draw->AddRectFilled(ImVec2(separatorX, cornerY), ImVec2(separatorX + 1.0f * scale, cornerY + 15.0f * scale),
                            ColorWithAlpha(180, 140, 232, 0.40f * cornerRise));
        DrawTrackedText(draw, textFont, separatorX + 13.0f * scale, cornerY, cornerSize, cornerColor, "60 H LEFT",
                        2.0f * scale);

        const char* kLocation = "TERMINA FIELD";
        const float locationTracking = 4.0f * scale;
        const float locationWidth = TrackedTextWidth(textFont, cornerSize, kLocation, locationTracking);
        DrawTrackedText(draw, textFont, origin.x + size.x - 34.0f * scale - locationWidth, cornerY, cornerSize,
                        cornerColor, kLocation, locationTracking);
    }

  private:
    static void DrawCenteredText(ImDrawList* draw, ImFont* font, float centerX, float y, float pixelSize,
                                 ImU32 color, const char* text, float tracking) {
        const float width = TrackedTextWidth(font, pixelSize, text, tracking);
        DrawTrackedText(draw, font, centerX - width * 0.5f, y, pixelSize, color, text, tracking);
    }

    bool mWasDrawing = false;
    double mShownAt = 0.0;
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
