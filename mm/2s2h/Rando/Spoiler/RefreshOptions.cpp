#include "Spoiler.h"
#include <public/bridge/consolevariablebridge.h>
#include <filesystem>
#include "BenPort.h"
#include <spdlog/spdlog.h>

#include <libultraship/libultra/types.h>

std::vector<std::string> Rando::Spoiler::spoilerOptions;

static std::filesystem::path GetRandomizerFolderPath() {
    return Ship::Context::GetPathRelativeToAppDirectory("randomizer", appShortName);
}

// This function refreshes the list of spoiler files in the randomizer folder, this list is used in the Randomizer UI,
// and also includes an option to generate a new seed at the top of the list.
void Rando::Spoiler::RefreshOptions() {
    Rando::Spoiler::spoilerOptions.clear();

    Rando::Spoiler::spoilerOptions.push_back("Generate New Seed");
    s32 spoilerFileIndex = -1;

    std::error_code ec;
    std::filesystem::path randomizerFolderPath = GetRandomizerFolderPath();
    std::filesystem::create_directories(randomizerFolderPath, ec);
    if (ec) {
        SPDLOG_ERROR("Failed to create randomizer folder '{}': {}", randomizerFolderPath.string(), ec.message());
        CVarSetInteger("gRando.SpoilerFileIndex", 0);
        CVarSetString("gRando.SpoilerFile", "");
        return;
    }

    // Add all files in the randomizer folder to the list of spoiler options
    std::filesystem::directory_iterator it(randomizerFolderPath, ec);
    if (ec) {
        SPDLOG_ERROR("Failed to scan randomizer folder '{}': {}", randomizerFolderPath.string(), ec.message());
        CVarSetInteger("gRando.SpoilerFileIndex", 0);
        CVarSetString("gRando.SpoilerFile", "");
        return;
    }

    std::filesystem::directory_iterator end;
    while (it != end) {
        const auto& entry = *it;
        if (ec) {
            SPDLOG_ERROR("Failed to scan randomizer folder '{}': {}", randomizerFolderPath.string(), ec.message());
            CVarSetInteger("gRando.SpoilerFileIndex", 0);
            CVarSetString("gRando.SpoilerFile", "");
            return;
        }
        if (entry.is_regular_file()) {
            std::string fileName = entry.path().filename().string();
            Rando::Spoiler::spoilerOptions.push_back(fileName);

            // Check if the current file is the one set in the cvar
            if (fileName == CVarGetString("gRando.SpoilerFile", "")) {
                spoilerFileIndex = Rando::Spoiler::spoilerOptions.size() - 1;
            }
        }

        it.increment(ec);
    }

    // If the current spoiler file is not in the randomizer folder, reset the cvar
    if (spoilerFileIndex == -1) {
        CVarSetInteger("gRando.SpoilerFileIndex", 0);
        CVarSetString("gRando.SpoilerFile", "");
    } else {
        CVarSetInteger("gRando.SpoilerFileIndex", spoilerFileIndex);
    }
}
