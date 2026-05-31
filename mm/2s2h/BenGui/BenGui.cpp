#include "BenGui.hpp"

#include <spdlog/spdlog.h>
#include <imgui.h>
#define IMGUI_DEFINE_MATH_OPERATORS
#include <imgui_internal.h>
#include <libultraship/libultraship.h>
#include <graphic/Fast3D/gfx_pc.h>
#include "UIWidgets.hpp"
#include "HudEditor.h"
#include "CosmeticEditor.h"
#include "2s2h/Enhancements/Audio/AudioEditor.h"
#include "Notification.h"
#include "BenModals.h"
#include "Rando/CheckTracker/CheckTracker.h"

#ifdef __APPLE__
#include "graphic/Fast3D/gfx_metal.h"
#endif

#ifdef __SWITCH__
#include <port/switch/SwitchImpl.h>
#endif

#include "include/global.h"
#include "include/z64audio.h"

#include "Enhancements/Trackers/ItemTracker.h"
#include "Enhancements/Trackers/ItemTrackerSettings.h"
#include "Enhancements/Trackers/DisplayOverlay.h"
#include "Enhancements/Trackers/TimeSplits/Timesplits.h"
#include "Enhancements/Trackers/TimeSplits/TimesplitsSettings.h"

namespace BenGui {
// MARK: - Delegates

std::shared_ptr<BenMenuBar> mBenMenuBar;

std::shared_ptr<Ship::GuiWindow> mConsoleWindow;
std::shared_ptr<Ship::GuiWindow> mStatsWindow;
std::shared_ptr<Ship::GuiWindow> mGfxDebuggerWindow;

std::shared_ptr<SaveEditorWindow> mSaveEditorWindow;
std::shared_ptr<HudEditorWindow> mHudEditorWindow;
std::shared_ptr<CosmeticEditorWindow> mCosmeticEditorWindow;
std::shared_ptr<AudioEditor> mAudioEditorWindow;
std::shared_ptr<ActorViewerWindow> mActorViewerWindow;
std::shared_ptr<CollisionViewerWindow> mCollisionViewerWindow;
std::shared_ptr<EventLogWindow> mEventLogWindow;
std::shared_ptr<BenMenu> mBenMenu;
std::shared_ptr<BenModalWindow> mModalWindow;
std::shared_ptr<BenInputEditorWindow> mBenInputEditorWindow;
std::shared_ptr<Notification::Window> mNotificationWindow;
std::shared_ptr<ItemTrackerWindow> mItemTrackerWindow;
std::shared_ptr<ItemTrackerSettingsWindow> mItemTrackerSettingsWindow;
std::shared_ptr<DisplayOverlayWindow> mDisplayOverlayWindow;
std::shared_ptr<TimesplitsWindow> mTimesplitsWindow;
std::shared_ptr<TimesplitsSettingsWindow> mTimesplitsSettingsWindow;
std::shared_ptr<Rando::CheckTracker::CheckTrackerWindow> mRandoCheckTrackerWindow;
std::shared_ptr<Rando::CheckTracker::SettingsWindow> mRandoCheckTrackerSettingsWindow;

UIWidgets::Colors GetMenuThemeColor() {
    return mBenMenu != nullptr ? mBenMenu->GetMenuThemeColor() : UIWidgets::Colors::Blue;
}

void SetupGuiElements() {
    auto gui = Ship::Context::GetInstance()->GetWindow()->GetGui();

    auto& style = ImGui::GetStyle();
    style.FramePadding = ImVec2(4.0f, 6.0f);
    style.ItemSpacing = ImVec2(8.0f, 6.0f);
    style.Colors[ImGuiCol_MenuBarBg] = UIWidgets::ColorValues.at(UIWidgets::Colors::DarkGray);

#ifdef __ANDROID__
    CVarClear(CVAR_MENU_BAR_OPEN);
#endif
    mBenMenuBar = std::make_shared<BenMenuBar>(CVAR_MENU_BAR_OPEN, CVarGetInteger(CVAR_MENU_BAR_OPEN, 0));
    gui->SetMenuBar(std::reinterpret_pointer_cast<Ship::GuiMenuBar>(mBenMenuBar));

    if (!gui->GetMenuBar() && !CVarGetInteger("gSettings.DisableMenuShortcutNotify", 0)) {
#if defined(__SWITCH__) || defined(__WIIU__) || defined(__ANDROID__)
        gui->GetGameOverlay()->TextDrawNotification(30.0f, true, "Press - to access enhancements menu");
#else
        gui->GetGameOverlay()->TextDrawNotification(30.0f, true, "Press F1 to access enhancements menu");
#endif
    }

    mBenMenu = std::make_shared<BenMenu>("gWindows.Menu", "Settings Menu");
    gui->SetMenu(mBenMenu);

    mStatsWindow = gui->GetGuiWindow("Stats");
    if (mStatsWindow == nullptr) {
        SPDLOG_ERROR("Could not find stats window");
    }

    mConsoleWindow = gui->GetGuiWindow("Console");
    if (mConsoleWindow == nullptr) {
        SPDLOG_ERROR("Could not find console window");
    }

    mGfxDebuggerWindow = gui->GetGuiWindow("GfxDebuggerWindow");
    if (mGfxDebuggerWindow == nullptr) {
        SPDLOG_ERROR("Could not find input GfxDebuggerWindow");
    }

    mBenInputEditorWindow = std::make_shared<BenInputEditorWindow>("gWindows.BenInputEditor", "2S2H Input Editor");
    gui->AddGuiWindow(mBenInputEditorWindow);

    mSaveEditorWindow = std::make_shared<SaveEditorWindow>("gWindows.SaveEditor", "Save Editor", ImVec2(480, 600));
    gui->AddGuiWindow(mSaveEditorWindow);

    mHudEditorWindow = std::make_shared<HudEditorWindow>("gWindows.HudEditor", "HUD Editor", ImVec2(480, 600));
    gui->AddGuiWindow(mHudEditorWindow);

    mCosmeticEditorWindow =
        std::make_shared<CosmeticEditorWindow>("gWindows.CosmeticEditor", "Cosmetic Editor", ImVec2(480, 600));
    gui->AddGuiWindow(mCosmeticEditorWindow);

    mAudioEditorWindow = std::make_shared<AudioEditor>("gWindows.AudioEditor", "Audio Editor", ImVec2(520, 600));
    gui->AddGuiWindow(mAudioEditorWindow);

    mActorViewerWindow = std::make_shared<ActorViewerWindow>("gWindows.ActorViewer", "Actor Viewer", ImVec2(520, 600));
    gui->AddGuiWindow(mActorViewerWindow);

    mCollisionViewerWindow =
        std::make_shared<CollisionViewerWindow>("gWindows.CollisionViewer", "Collision Viewer", ImVec2(390, 475));
    gui->AddGuiWindow(mCollisionViewerWindow);

    mEventLogWindow = std::make_shared<EventLogWindow>("gWindows.EventLog", "Event Log", ImVec2(520, 600));
    gui->AddGuiWindow(mEventLogWindow);

    mItemTrackerWindow = std::make_shared<ItemTrackerWindow>("gWindows.ItemTracker", "Item Tracker");
    gui->AddGuiWindow(mItemTrackerWindow);

    mItemTrackerSettingsWindow = std::make_shared<ItemTrackerSettingsWindow>("gWindows.ItemTrackerSettings",
                                                                             "Item Tracker Settings", ImVec2(800, 400));
    gui->AddGuiWindow(mItemTrackerSettingsWindow);

    mDisplayOverlayWindow = std::make_shared<DisplayOverlayWindow>("gWindows.DisplayOverlay", "Display Overlay");
    gui->AddGuiWindow(mDisplayOverlayWindow);

    mTimesplitsWindow = std::make_shared<TimesplitsWindow>("gWindows.Timesplits", "Time Splits Window");
    gui->AddGuiWindow(mTimesplitsWindow);

    mTimesplitsSettingsWindow = std::make_shared<TimesplitsSettingsWindow>(
        "gWindows.Timesplits.Settings", "Time Splits Settings Window", ImVec2(567, 97));
    gui->AddGuiWindow(mTimesplitsSettingsWindow);

    mModalWindow = std::make_shared<BenModalWindow>("gWindows.Modals", "Modals");
    gui->AddGuiWindow(mModalWindow);
    mModalWindow->Show();

    mRandoCheckTrackerWindow =
        std::make_shared<Rando::CheckTracker::CheckTrackerWindow>("gWindows.CheckTracker", "Check Tracker");
    gui->AddGuiWindow(mRandoCheckTrackerWindow);

    mRandoCheckTrackerSettingsWindow = std::make_shared<Rando::CheckTracker::SettingsWindow>(
        "gWindows.CheckTrackerSettings", "Check Tracker Settings", ImVec2(800, 400));
    gui->AddGuiWindow(mRandoCheckTrackerSettingsWindow);

    gui->SetPadBtnTogglesMenu();

    mNotificationWindow = std::make_shared<Notification::Window>("gWindows.Notifications", "Notifications Window");
    gui->AddGuiWindow(mNotificationWindow);
    mNotificationWindow->Show();
}

void Destroy() {
    auto gui = Ship::Context::GetInstance()->GetWindow()->GetGui();

    gui->RemoveAllGuiWindows();
    mBenMenuBar = nullptr;
    mBenMenu = nullptr;
    mStatsWindow = nullptr;
    mConsoleWindow = nullptr;
    mBenInputEditorWindow = nullptr;
    mGfxDebuggerWindow = nullptr;
    mCollisionViewerWindow = nullptr;
    mEventLogWindow = nullptr;
    mNotificationWindow = nullptr;
    mSaveEditorWindow = nullptr;
    mHudEditorWindow = nullptr;
    mCosmeticEditorWindow = nullptr;
    mAudioEditorWindow = nullptr;
    mActorViewerWindow = nullptr;
    mItemTrackerWindow = nullptr;
    mItemTrackerSettingsWindow = nullptr;
    mDisplayOverlayWindow = nullptr;
    mTimesplitsWindow = nullptr;
    mTimesplitsSettingsWindow = nullptr;
    mModalWindow = nullptr;
    mRandoCheckTrackerWindow = nullptr;
    mRandoCheckTrackerSettingsWindow = nullptr;
}

void RegisterPopup(std::string title, std::string message, std::string button1, std::string button2,
                   std::function<void()> button1callback, std::function<void()> button2callback) {
    if (mModalWindow != nullptr) {
        mModalWindow->RegisterPopup(title, message, button1, button2, button1callback, button2callback);
    }
}

size_t PopupsQueued() {
    return mModalWindow != nullptr ? mModalWindow->PopupsQueued() : 0;
}

bool DismissPopup(std::string title) {
    if (mModalWindow != nullptr && mModalWindow->IsPopupOpen(title)) {
        mModalWindow->DismissPopup();
        return true;
    }
    return false;
}

void SetDisplayOverlayVisibility(bool visible) {
    if (mDisplayOverlayWindow != nullptr) {
        if (visible) {
            mDisplayOverlayWindow->Show();
        } else {
            mDisplayOverlayWindow->Hide();
        }
    } else {
        CVarSetInteger("gWindows.DisplayOverlay", visible ? 1 : 0);
    }
    Ship::Context::GetInstance()->GetWindow()->GetGui()->SaveConsoleVariablesOnNextTick();
}
} // namespace BenGui
