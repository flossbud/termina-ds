#ifndef BenGui_hpp
#define BenGui_hpp

#include <stdio.h>
#include <functional>
#include <string>
#include "BenMenuBar.h"
#include "BenMenu.h"
#include "DeveloperTools/SaveEditor.h"
#include "DeveloperTools/ActorViewer.h"
#include "DeveloperTools/CollisionViewer.h"
#include "DeveloperTools/EventLog.h"
#include "BenInputEditorWindow.h"

namespace BenGui {
    void SetupHooks();
    void SetupGuiElements();
    void Draw();
    void Destroy();
    size_t PopupsQueued();
    void RegisterPopup(std::string title, std::string message, std::string button1 = "OK", std::string button2 = "",
                       std::function<void()> button1callback = nullptr,
                       std::function<void()> button2callback = nullptr);
    bool DismissPopup(std::string title);
    UIWidgets::Colors GetMenuThemeColor();
    void SetDisplayOverlayVisibility(bool visible);
}

#define THEME_COLOR BenGui::GetMenuThemeColor()

#endif /* BenGui_hpp */
