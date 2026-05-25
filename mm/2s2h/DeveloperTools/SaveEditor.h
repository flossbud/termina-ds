#pragma once

#include <libultraship/libultraship.h>
#include <vector>

extern "C" {
#include "z64save.h"
}

typedef enum {
    CURRENT_SCENE_FLAGS,
    WEEK_EVENT_REG,
    EVENT_INF,
    SCENES_VISIBLE,
    OWL_ACTIVATION,
    PERMANENT_SCENE_FLAGS,
    CYCLE_SCENE_FLAGS,
} FlagTableType;

typedef enum {
    NONE,
    PERSISTENT,
    CYCLE_RESET,
    SCENE_RESET,
} SaveEditorFlagType;

typedef struct {
    SaveEditorFlagType type;
    uint16_t flag;
    const char* description;
} FlagEntry;

typedef struct {
    const char* name;
    FlagTableType flagTableType;
    std::vector<FlagEntry> entries;
} FlagTable;

class SaveEditorWindow : public Ship::GuiWindow {
  public:
    using GuiWindow::GuiWindow;

    void InitElement() override;
    void DrawElement() override;
    void UpdateElement() override{};
};
