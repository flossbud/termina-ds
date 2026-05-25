#include "ClockShuffle.h"
#include "Rando/Logic/Logic.h"

int Rando::ClockItems::GetHalfDayIndexFromClockItem(RandoItemId clockItemId) {
    switch (clockItemId) {
        case RI_TIME_DAY_1:
            return HALF_DAY1_DAY;
        case RI_TIME_NIGHT_1:
            return HALF_DAY1_NIGHT;
        case RI_TIME_DAY_2:
            return HALF_DAY2_DAY;
        case RI_TIME_NIGHT_2:
            return HALF_DAY2_NIGHT;
        case RI_TIME_DAY_3:
            return HALF_DAY3_DAY;
        case RI_TIME_NIGHT_3:
            return HALF_DAY3_NIGHT;
        default:
            return INVALID;
    }
}

RandoItemId Rando::ClockItems::GetClockItemFromHalfDayIndex(int halfDayIndex) {
    static const RandoItemId clockItemMap[] = {
        RI_TIME_DAY_1, RI_TIME_NIGHT_1, RI_TIME_DAY_2, RI_TIME_NIGHT_2, RI_TIME_DAY_3, RI_TIME_NIGHT_3,
    };

    if (halfDayIndex < 0 || halfDayIndex >= HALF_COUNT) {
        return RI_UNKNOWN;
    }
    return clockItemMap[halfDayIndex];
}

u8 Rando::ClockItems::GetAllOwnedHalfDaysMask() {
    u8 ownedMask = 0;
    for (int i = 0; i < HALF_COUNT; ++i) {
        if (Rando::Logic::OwnsClockHalfDay(i)) {
            ownedMask |= (1 << i);
        }
    }
    return ownedMask;
}

int Rando::ClockItems::FindOwnedHalfDay(bool fromEnd) {
    if (fromEnd) {
        for (int i = HALF_COUNT - 1; i >= 0; --i) {
            if (Rando::Logic::OwnsClockHalfDay(i)) {
                return i;
            }
        }
    } else {
        for (int i = 0; i < HALF_COUNT; ++i) {
            if (Rando::Logic::OwnsClockHalfDay(i)) {
                return i;
            }
        }
    }
    return INVALID;
}

bool Rando::ClockItems::IsClockItem(RandoItemId itemId) {
    return (itemId >= RI_TIME_DAY_1 && itemId <= RI_TIME_NIGHT_3) || itemId == RI_TIME_PROGRESSIVE;
}

bool Rando::ClockItems::IsDayClock(RandoItemId itemId) {
    return itemId == RI_TIME_DAY_1 || itemId == RI_TIME_DAY_2 || itemId == RI_TIME_DAY_3;
}

void Rando::ClockShuffle::OnFileLoad() {
}

void Rando::ClockShuffle::SetTimeToHalfDayStart(int halfDayIndex) {
}

bool Rando::ClockShuffle::IsTimeOwnedForClockShuffle(s32 day, u16 time) {
    return true;
}

std::string Rando::ClockShuffle::GetTimeDescriptionForMessage(s32 day, u16 time) {
    return "";
}
