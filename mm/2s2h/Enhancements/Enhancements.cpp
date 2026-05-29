#include "Enhancements.h"

void RegisterPictoBoxOnCUp();

void InitEnhancements() {
    // Camera
    RegisterCameraInterpolationFixes();
    RegisterCameraFreeLook();
    RegisterDebugCam();

    // Cheats
    RegisterInfiniteCheats();
    RegisterLongerFlowerGlide();
    RegisterMoonJumpOnL();
    RegisterUnbreakableRazorSword();
    RegisterUnrestrictedItems();
    RegisterHookshotAnywhere();
    RegisterClimbAnywhere();
    RegisterEasyFrameAdvance();
    RegisterInfiniteEponaCarrots();
    RegisterTimeStopInTemples();
    RegisterElegyAnywhere();

    // Clock
    RegisterTextBasedClock();
    Register3DSClock();

    // Cycle & Saving
    RegisterEndOfCycleSaveHooks();
    RegisterMoonCrashSave();
    RegisterSavingEnhancements();
    RegisterAutosave();
    RegisterKeepExpressMail();

    // Dialogue
    RegisterFastBankSelection();

    // Time Savers
    RegisterFastChests();
    RegisterFasterRupeeAccumulator();
    RegisterFasterSceneTransitions();

    // Equipment
    RegisterSkipMagicArrowEquip();
    RegisterInstantRecall();
    RegisterRemoteBombchu();
    RegisterBombArrows();
    RegisterArrowCycle();
    RegisterPictoBoxOnCUp();

    // Fixes
    RegisterFierceDeityZTargetMovement();
    RegisterTwoHandedSwordSpinAttack();

    // Graphics
    RegisterDisableBlackBars();
    Register3DItemDrops();

    // Masks
    RegisterFastTransformation();
    RegisterFierceDeityAnywhere();
    RegisterBlastMaskKeg();
    RegisterNoBlastMaskCooldown();
    RegisterPersistentMasks();

    // Minigames
    RegisterAlwaysWinDoggyRace();
    RegisterCremiaHugs();
    RegisterFrogChoirCount();
    RegisterSwordsmanSchool();

    // Player
    RegisterClimbSpeed();
    RegisterFastFlowerLaunch();
    RegisterInstantPutaway();
    RegisterFierceDeityPutaway();
    RegisterLinkSpeedModifier();
    RegisterFasterPushAndPull();
    RegisterPreventDiveOverWater();
    RegisterUnsheatheWithoutSlashing();

    // Songs
    RegisterEnableSunsSong();
    RegisterFasterSongPlayback();
    RegisterPauseOwlWarp();
    RegisterZoraEggCount();
    RegisterSkipScarecrowSong();

    // Restorations
    RegisterPowerCrouchStab();
    RegisterSideRoll();
    RegisterTatlISG();
    RegisterVariableFlipHop();
    RegisterWoodfallMountainAppearance();

    // Cutscenes
    RegisterCutscenes();

    // Modes
    RegisterPlayAsKafei();
    RegisterTimeMovesWhenYouMove();

    // Difficulty Options
    RegisterCustomBankRewardThresholds();
    RegisterDamageMultiplier();
    RegisterShowDekuGuardSearchBalls();
    RegisterDisableTakkuriSteal();
    RegisterGibdoTradeSequenceOptions();
    RegisterGoronRaceDifficulty();
    RegisterHiddenGrottosVisibility();
    RegisterHyperEnemies();
    RegisterJinxedTimer();
    RegisterPermanentHeartLoss();
}
