#ifndef Z64MSGEVENT_H
#define Z64MSGEVENT_H

#include "PR/ultratypes.h"

struct PlayState;
struct Actor;

typedef enum {
    MSCRIPT_CMD_00,
    MSCRIPT_CMD_01,
    MSCRIPT_CMD_02,
    MSCRIPT_CMD_03,
    MSCRIPT_CMD_04,
    MSCRIPT_CMD_05,
    MSCRIPT_CMD_06,
    MSCRIPT_CMD_07,
    MSCRIPT_CMD_08,
    MSCRIPT_CMD_09,
    MSCRIPT_CMD_10,
    MSCRIPT_CMD_11,
    MSCRIPT_CMD_12,
    MSCRIPT_CMD_13,
    MSCRIPT_CMD_14,
    MSCRIPT_CMD_15,
    MSCRIPT_CMD_16,
    MSCRIPT_CMD_17,
    MSCRIPT_CMD_18,
    MSCRIPT_CMD_19,
    MSCRIPT_CMD_20,
    MSCRIPT_CMD_21,
    MSCRIPT_CMD_22,
    MSCRIPT_CMD_23,
    MSCRIPT_CMD_24,
    MSCRIPT_CMD_25,
    MSCRIPT_CMD_26,
    MSCRIPT_CMD_27,
    MSCRIPT_CMD_28,
    MSCRIPT_CMD_29,
    MSCRIPT_CMD_30,
    MSCRIPT_CMD_31,
    MSCRIPT_CMD_32,
    MSCRIPT_CMD_33,
    MSCRIPT_CMD_34,
    MSCRIPT_CMD_35,
    MSCRIPT_CMD_36,
    MSCRIPT_CMD_BRANCH_ON_ITEM,
    MSCRIPT_CMD_38,
    MSCRIPT_CMD_39,
    MSCRIPT_CMD_40,
    MSCRIPT_CMD_41,
    MSCRIPT_CMD_DEL_ITEM,
    MSCRIPT_CMD_43,
    MSCRIPT_CMD_44,
    MSCRIPT_CMD_45,
    MSCRIPT_CMD_46,
    MSCRIPT_CMD_47,
    MSCRIPT_CMD_48,
    MSCRIPT_CMD_49,
    MSCRIPT_CMD_50
} MsgScriptCmd;

#define MSCRIPT_CMD_ID_CHECK_WEEK_EVENT_REG MSCRIPT_CMD_00
#define MSCRIPT_CMD_ID_CHECK_GORON MSCRIPT_CMD_01
#define MSCRIPT_CMD_ID_CHECK_ZORA MSCRIPT_CMD_02
#define MSCRIPT_CMD_ID_CHECK_DEKU MSCRIPT_CMD_03
#define MSCRIPT_CMD_ID_CHECK_HUMAN MSCRIPT_CMD_04
#define MSCRIPT_CMD_ID_CHECK_TEXT_CHOICE MSCRIPT_CMD_05
#define MSCRIPT_CMD_ID_OFFER_ITEM MSCRIPT_CMD_06
#define MSCRIPT_CMD_ID_AUTOTALK MSCRIPT_CMD_07
#define MSCRIPT_CMD_ID_CHECK_RUPEES MSCRIPT_CMD_08
#define MSCRIPT_CMD_ID_CHECK_CALLBACK MSCRIPT_CMD_09
#define MSCRIPT_CMD_ID_CHECK_DAY MSCRIPT_CMD_10
#define MSCRIPT_CMD_ID_AWAIT_TEXT_JUMP MSCRIPT_CMD_11
#define MSCRIPT_CMD_ID_AWAIT_TEXT MSCRIPT_CMD_12
#define MSCRIPT_CMD_ID_AWAIT_TEXT_END MSCRIPT_CMD_13
#define MSCRIPT_CMD_ID_BEGIN_TEXT MSCRIPT_CMD_14
#define MSCRIPT_CMD_ID_CONTINUE_TEXT MSCRIPT_CMD_15
#define MSCRIPT_CMD_ID_DONE MSCRIPT_CMD_16
#define MSCRIPT_CMD_ID_SET_WEEK_EVENT_REG MSCRIPT_CMD_17
#define MSCRIPT_CMD_ID_CLOSE_TEXT MSCRIPT_CMD_18
#define MSCRIPT_CMD_ID_SET_COLLECTIBLE MSCRIPT_CMD_19
#define MSCRIPT_CMD_ID_CHANGE_RUPEES MSCRIPT_CMD_20
#define MSCRIPT_CMD_ID_PAUSE MSCRIPT_CMD_21
#define MSCRIPT_CMD_ID_UNSET_AUTOTALK MSCRIPT_CMD_22
#define MSCRIPT_CMD_ID_FOCUS_TO_CHILD MSCRIPT_CMD_23
#define MSCRIPT_CMD_ID_FOCUS_TO_SELF MSCRIPT_CMD_24
#define MSCRIPT_CMD_ID_JUMP MSCRIPT_CMD_25
#define MSCRIPT_CMD_ID_CHECK_QUEST_ITEM MSCRIPT_CMD_26
#define MSCRIPT_CMD_ID_CHECK_EVENT_INF MSCRIPT_CMD_27
#define MSCRIPT_CMD_ID_SET_EVENT_INF MSCRIPT_CMD_28
#define MSCRIPT_CMD_ID_UNSET_EVENT_INF MSCRIPT_CMD_29
#define MSCRIPT_CMD_ID_CHECK_ITEM_ACTION MSCRIPT_CMD_30
#define MSCRIPT_CMD_ID_CHECK_HAS_SONG MSCRIPT_CMD_31
#define MSCRIPT_CMD_ID_CHECK_WORN_MASK MSCRIPT_CMD_32
#define MSCRIPT_CMD_ID_CHECK_AFTER_TIME MSCRIPT_CMD_33
#define MSCRIPT_CMD_ID_CHECK_BEFORE_TIME MSCRIPT_CMD_34
#define MSCRIPT_CMD_ID_CHECK_SWITCH_FLAG MSCRIPT_CMD_35
#define MSCRIPT_CMD_ID_SET_SWITCH_FLAG MSCRIPT_CMD_36
#define MSCRIPT_CMD_ID_CHECK_ITEM MSCRIPT_CMD_BRANCH_ON_ITEM
#define MSCRIPT_CMD_ID_CHECK_BETWEEN_TIME MSCRIPT_CMD_38
#define MSCRIPT_CMD_ID_CHECK_ON_DAY MSCRIPT_CMD_39
#define MSCRIPT_CMD_ID_CHECK_CALLBACK_CONTINUE MSCRIPT_CMD_40
#define MSCRIPT_CMD_ID_CHECK_HAS_POWDER_KEG MSCRIPT_CMD_41
#define MSCRIPT_CMD_ID_DELETE_ITEM MSCRIPT_CMD_DEL_ITEM
#define MSCRIPT_CMD_ID_CHECK_CALLBACK_MULTI MSCRIPT_CMD_43
#define MSCRIPT_CMD_ID_PLAYER_TALK MSCRIPT_CMD_44
#define MSCRIPT_CMD_ID_NOTEBOOK_EVENT MSCRIPT_CMD_45
#define MSCRIPT_CMD_ID_AWAIT_TEXT_DONE MSCRIPT_CMD_46
#define MSCRIPT_CMD_ID_JUMP_3 MSCRIPT_CMD_47
#define MSCRIPT_CMD_ID_PLAY_DECIDE MSCRIPT_CMD_48
#define MSCRIPT_CMD_ID_PLAY_CANCEL MSCRIPT_CMD_49
#define MSCRIPT_CMD_ID_PLAY_ERROR MSCRIPT_CMD_50

typedef u8 MsgScript;

typedef s32 (*MsgEventCallback)(struct Actor* thisx, struct PlayState* play);
typedef MsgEventCallback MsgScriptCallback;

typedef s32 (*MsgEventHandler)(struct Actor* actor, struct PlayState* play, u8** scriptPtr, MsgEventCallback callback, s32*);

typedef struct {
    /* 0x0 */ u8 cmd;
} MsgScriptCmdBase;

typedef struct {
    /* 0x0 */ MsgScriptCmdBase base;
    /* 0x1 */ u8 offset0H;
    /* 0x2 */ u8 offset0L;
    /* 0x3 */ u8 offset1H;
    /* 0x4 */ u8 offset1L;
    /* 0x5 */ u8 offset2H;
    /* 0x6 */ u8 offset2L;
} MsgScriptCmdCheckTextChoice;

typedef struct {
    /* 0x0 */ MsgScriptCmdBase base;
    /* 0x1 */ u8 itemIdH;
    /* 0x2 */ u8 itemIdL;
    /* 0x3 */ u8 offsetH;
    /* 0x4 */ u8 offsetL;
} MsgScriptCmdOfferItem;

typedef struct {
    /* 0x0 */ MsgScriptCmdBase base;
    /* 0x1 */ u8 textIdH;
    /* 0x2 */ u8 textIdL;
} MsgScriptCmdBeginText;

typedef struct {
    /* 0x0 */ MsgScriptCmdBase base;
    /* 0x1 */ u8 textIdH;
    /* 0x2 */ u8 textIdL;
} MsgScriptCmdContinueText;

typedef struct {
    /* 0x0 */ MsgScriptCmdBase base;
    /* 0x1 */ u8 itemH;
    /* 0x2 */ u8 itemL;
    /* 0x3 */ u8 offsetH;
    /* 0x4 */ u8 offsetL;
} MsgScriptCmdCheckItem;

#define MSCRIPT_GET_8(script, i) ((script)[i])
#define MSCRIPT_GET_16(script, i) ((((script)[i + 1] | ((script)[i] << 8))))
#define SCRIPT_PACK_16(h, l) (((h) << 8) | (l))
#define SCRIPT_UNPACK_8(v) (u8)(v)
#define SCRIPT_UNPACK_16(v) (u8)_SHIFTR(v, 8, 8), (u8)_SHIFTR(v, 0, 8)

// Command macros

#define MSCRIPT_PACK_8(v) (u8)(v)
#define MSCRIPT_PACK_16(v) (u8)_SHIFTR(v, 8, 8), (u8)_SHIFTR(v, 0, 8)

/**
 * 
 * Size = 5
 */ 
#define MSCRIPT_BRANCH_ON_WEEK_EVENT_REG(flags, test, skip) \
    MSCRIPT_CMD_00, MSCRIPT_PACK_8(flags), MSCRIPT_PACK_8(test), MSCRIPT_PACK_16(skip)

#define MSCRIPT_BRANCH_ON_WEEK_EVENT_REG_LENGTH 5

/**
 * 
 * Size = 3
 */
#define MSCRIPT_BRANCH_IF_GORON(skip) \
    MSCRIPT_CMD_01, MSCRIPT_PACK_16(skip)

/**
 * 
 * Size = 3
 */
#define MSCRIPT_BRANCH_IF_ZORA(skip) \
    MSCRIPT_CMD_02, MSCRIPT_PACK_16(skip)

/**
 * 
 * Size = 3
 */
#define MSCRIPT_BRANCH_IF_DEKU(skip) \
    MSCRIPT_CMD_03, MSCRIPT_PACK_16(skip)

/**
 * 
 * Size = 3
 */
#define MSCRIPT_BRANCH_IF_HUMAN(skip) \
    MSCRIPT_CMD_04, MSCRIPT_PACK_16(skip)

/**
 * 
 * Size = 7
 */
#define MSCRIPT_BRANCH_ON_TEXT_CHOICE(skip0, skip1, skip2) \
    MSCRIPT_CMD_05, MSCRIPT_PACK_16(skip0), MSCRIPT_PACK_16(skip1), MSCRIPT_PACK_16(skip2)

/**
 * 
 * Size = 5
 */
#define MSCRIPT_OFFER_ITEM(unk, skip) \
    MSCRIPT_CMD_06, MSCRIPT_PACK_16(unk), MSCRIPT_PACK_16(skip)

/**
 * Size = 3
 */
#define MSCRIPT_CMD07(skip) \
    MSCRIPT_CMD_07, MSCRIPT_PACK_16(skip)

/**
 * Size = 5
 */
#define MSCRIPT_BRANCH_ON_RUPEES(rupees, skip) \
    MSCRIPT_CMD_08, MSCRIPT_PACK_16(rupees), MSCRIPT_PACK_16(skip)

/**
 * Size = 3
 */
#define MSCRIPT_BRANCH_ON_CALLBACK_OPTIONAL(skip) \
    MSCRIPT_CMD_09, MSCRIPT_PACK_16(skip)

/**
 * Size = 13
 */
#define MSCRIPT_BRANCH_ON_DAY(skipDay1, skipNight1, skipDay2, skipNight2, skipDay3, skipNight3) \
    MSCRIPT_CMD_10, \
    MSCRIPT_PACK_16(skipDay1), MSCRIPT_PACK_16(skipNight1), \
    MSCRIPT_PACK_16(skipDay2), MSCRIPT_PACK_16(skipNight2), \
    MSCRIPT_PACK_16(skipDay3), MSCRIPT_PACK_16(skipNight3)

/**
 * Size = 3
 */
#define MSCRIPT_CMD11(skip) \
    MSCRIPT_CMD_11, MSCRIPT_PACK_16(skip)

/**
 * Size = 1
 */
#define MSCRIPT_AWAIT_TEXT() \
    MSCRIPT_CMD_12

/**
 * Size = 1
 */
#define MSCRIPT_CMD13() \
    MSCRIPT_CMD_13

/**
 * Size = 3
 */
#define MSCRIPT_BEGIN_TEXT(arg) \
    MSCRIPT_CMD_14, MSCRIPT_PACK_16(arg)

/**
 * Size = 3
 */
#define MSCRIPT_CONTINUE_TEXT(arg) \
    MSCRIPT_CMD_15, MSCRIPT_PACK_16(arg)

/**
 * Size = 1
 */
#define MSCRIPT_DONE() \
    MSCRIPT_CMD_16

/**
 * Size = 3
 */
#define MSCRIPT_WEEK_EVENT_REG_SET(flags, value) \
    MSCRIPT_CMD_17, MSCRIPT_PACK_8(flags), MSCRIPT_PACK_8(value)

/**
 * Size = 1
 */
#define MSCRIPT_CLOSE_TEXT() \
    MSCRIPT_CMD_18

/**
 * Size = 3
 */
#define MSCRIPT_COLLECT_SET(collectFlag) \
    MSCRIPT_CMD_19, MSCRIPT_PACK_16(collectFlag)

/**
 * Size = 3
 */
#define MSCRIPT_CHANGE_RUPEES(unk) \
    MSCRIPT_CMD_20, MSCRIPT_PACK_16(unk)

/**
 * Size = 1
 */
#define MSCRIPT_PAUSE() \
    MSCRIPT_CMD_21

/**
 * Size = 1
 */
#define MSCRIPT_CMD22() \
    MSCRIPT_CMD_22

/**
 * Size = 1
 */
#define MSCRIPT_FOCUS_TO_CHILD() \
    MSCRIPT_CMD_23

/**
 * Size = 1
 */
#define MSCRIPT_FOCUS_TO_SELF() \
    MSCRIPT_CMD_24

/**
 * Size = 3
 */
#define MSCRIPT_JUMP(skip) \
    MSCRIPT_CMD_25, MSCRIPT_PACK_16(skip)

/**
 * Size = 5
 */
#define MSCRIPT_BRANCH_ON_QUEST_ITEM(questItem, skip) \
    MSCRIPT_CMD_26, MSCRIPT_PACK_16(questItem), MSCRIPT_PACK_16(skip)

/**
 * Size = 5
 */
#define MSCRIPT_BRANCH_ON_EVENT_INF(flag, value, skip) \
    MSCRIPT_CMD_27, MSCRIPT_PACK_8(flag), MSCRIPT_PACK_8(value), MSCRIPT_PACK_16(skip)

/**
 * Size = 3
 */
#define MSCRIPT_SET_EVENT_INF(flag, value) \
    MSCRIPT_CMD_28, MSCRIPT_PACK_8(flag), MSCRIPT_PACK_8(value)

/**
 * Size = 3
 */
#define MSCRIPT_UNSET_EVENT_INF(flag, value) \
    MSCRIPT_CMD_29, MSCRIPT_PACK_8(flag), MSCRIPT_PACK_8(value)

/**
 * Size = 9
 */
#define MSCRIPT_BRANCH_ON_ITEMACTION(unk, skipEqual, skipDefault, skipUnk) \
    MSCRIPT_CMD_30, MSCRIPT_PACK_16(unk), MSCRIPT_PACK_16(skipEqual), MSCRIPT_PACK_16(skipDefault), MSCRIPT_PACK_16(skipUnk)

/**
 * Size = 5
 */
#define MSCRIPT_BRANCH_ON_SONG_OBTAINED(questItem, skip) \
    MSCRIPT_CMD_31, MSCRIPT_PACK_16(questItem), MSCRIPT_PACK_16(skip)

/**
 * Size = 5
 */
#define MSCRIPT_BRANCH_ON_WORN_MASK(mask, skip) \
    MSCRIPT_CMD_32, MSCRIPT_PACK_16(mask), MSCRIPT_PACK_16(skip)

/**
 * Size = 5
 */
#define MSCRIPT_BRANCH_ON_TIME_GT(hours, minutes, skip) \
    MSCRIPT_CMD_33, MSCRIPT_PACK_8(hours), MSCRIPT_PACK_8(minutes), MSCRIPT_PACK_16(skip)

/**
 * Size = 5
 */
#define MSCRIPT_BRANCH_ON_TIME_LEQ(hours, minutes, skip) \
    MSCRIPT_CMD_34, MSCRIPT_PACK_8(hours), MSCRIPT_PACK_8(minutes), MSCRIPT_PACK_16(skip)

/**
 * Size = 5
 */
#define MSCRIPT_BRANCH_ON_SWITCH_FLAG(switchFlag, skip) \
    MSCRIPT_CMD_35, MSCRIPT_PACK_16(switchFlag), MSCRIPT_PACK_16(skip)

/**
 * Size = 3
 */
#define MSCRIPT_SET_SWITCH_FLAG(switchFlag) \
    MSCRIPT_CMD_36, MSCRIPT_PACK_16(switchFlag)

/**
 * Size = 5
 */
#define MSCRIPT_BRANCH_ON_ITEM(item, skip) \
    MSCRIPT_CMD_BRANCH_ON_ITEM, MSCRIPT_PACK_16(item), MSCRIPT_PACK_16(skip)

/**
 * Size = 7
 */
#define MSCRIPT_BRANCH_ON_TIME_INTERVAL(hours1, minutes1, hours2, minutes2, skip) \
    MSCRIPT_CMD_38, MSCRIPT_PACK_8(hours1), MSCRIPT_PACK_8(minutes1), MSCRIPT_PACK_8(hours2), MSCRIPT_PACK_8(minutes2), MSCRIPT_PACK_16(skip)

/**
 * Size = 5
 */
#define MSCRIPT_BRANCH_IF_DAY(day, skip) \
    MSCRIPT_CMD_39, MSCRIPT_PACK_16(day), MSCRIPT_PACK_16(skip)

/**
 * Size = 3
 */
#define MSCRIPT_BRANCH_ON_CALLBACK_2(skip) \
    MSCRIPT_CMD_40, MSCRIPT_PACK_16(skip)

/**
 * Size = 3
 */
#define MSCRIPT_HAS_POWDER_KEG(skip) \
    MSCRIPT_CMD_41, MSCRIPT_PACK_16(skip)

/**
 * Size = 3
 */
#define MSCRIPT_DEL_ITEM(item) \
    MSCRIPT_CMD_DEL_ITEM, MSCRIPT_PACK_16(item)

/**
 * Size = 7
 */
#define MSCRIPT_BRANCH_ON_CALLBACK(skip1, skip2, skip3) \
    MSCRIPT_CMD_43, MSCRIPT_PACK_16(skip1), MSCRIPT_PACK_16(skip2), MSCRIPT_PACK_16(skip3)

/**
 * Size = 3
 */
#define MSCRIPT_CMD44(textId) \
    MSCRIPT_CMD_44, MSCRIPT_PACK_16(textId)

/**
 * Size = 3
 */
#define MSCRIPT_NOTEBOOK_EVENT(event) \
    MSCRIPT_CMD_45, MSCRIPT_PACK_16(event)

/**
 * Size = 1
 */
#define MSCRIPT_CMD46() \
    MSCRIPT_CMD_46

/**
 * Size = 3
 */
#define MSCRIPT_JUMP_3(skip) \
    MSCRIPT_CMD_47, MSCRIPT_PACK_16(skip)

/**
 * Size = 1
 */
#define MSCRIPT_PLAY_DECIDE() \
    MSCRIPT_CMD_48

/**
 * Size = 1
 */
#define MSCRIPT_PLAY_CANCEL() \
    MSCRIPT_CMD_49

/**
 * Size = 1
 */
#define MSCRIPT_PLAY_ERROR() \
    MSCRIPT_CMD_50

#endif
