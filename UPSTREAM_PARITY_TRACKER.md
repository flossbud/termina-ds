# Upstream Parity Tracker

Last audited: 2026-05-31

Local branch: `android`
Primary upstream: `upstream/develop` (`HarbourMasters/2ship2harkinian`)
Reference fork: `jamer/develop` (`Jameriquiah/2ship2harkinian`)

This tracker is for Android parity work that needs adaptation instead of direct upstream merges. Keep release-only Android work separate from upstream feature parity when possible.

## Current Baseline

- Android release `v1.5.0` is published from `android`.
- Latest pushed Android commit during this audit: `8440c2a40 Harden SaveManager flash writes`.
- Shared merge-base with both upstream references: `cf63334c28886d1cb168e742ecbaef3a7900f81d`.
- The branch has many Android-specific commits, so raw ahead/behind counts are not useful by themselves. Track feature parity by commit/feature area instead.

## Recently Ported Or Verified

| Upstream area | Status | Notes |
| --- | --- | --- |
| Config updaters (`#1703`) | Ported | Local commit `aa484fb86 Port config updaters`. |
| Couple's Mask cutscene skip (`#1659`) | Ported | Local commits `dd56e15d2` and `e79801271` cover hook behavior. |
| Infinite Epona Carrots (`#1675`) | Ported | Local commit `bc3aa31f3`. |
| Mirrored World mode options (`#1636`) | Ported | Local commit `1a1b4ba54`. |
| Button environment color fix (`#1603`) | Ported | Local commit `6aee751c8`. |
| Scarecrow song through cycle reset (`#1692`, `#1707`) | Mostly ported | Local commit `945a47024`; current batch adds missing event-warning cleanup. |
| Bomb-arrow cycle ammo fix (`#1697`) | Verified present | Local code already has `OnPlayerReleaseHeldActor`, delayed bomb consumption, and held-expiry handling. |
| Color Pictograph (`#1484`) | Ported | Local release `v1.3.1`; verify against current upstream later for minor deltas. |
| Extended Projectile Interaction Distance (`#1681`) | Done locally | Ported source and enabled Android menu entry in the current local batch. |
| Song Items (`#1566`, `#1696`) | Done locally | Ported upstream source, enabled Android menu entry, and added gossip-stone softlock vanilla-behavior hooks. |
| Skip Enemy Cutscenes (`#1038`) | Verified present | Source is already ported and self-registering; Android menu entry is enabled in the current local batch. |
| Skip Soaring cutscene (`#1383`) | Done locally | Ported actor-init shortcut with Android/local EnTest7 names and enabled Android menu entry. |
| Sun's Song/Faster Song Playback ocarina end fix (`#1542`) | Done locally | Ported guard so Sun's Song does not force ocarina end while faster playback is active. |
| Hyrule Warriors Styled Link | Done locally | Ported missing source hook for an already-visible Android menu option. |
| Third save file slot toggle | Done locally | Ported missing reload hook and File 3 navigation gating for the existing Android menu option. |
| Fix Console Crashes | Done locally | Ported the missing reset helper and source crash guards for HESS/Weirdshot, Action Swap with no arrows, owl warp cursor loops, and remote hookshot floor-null handling. |
| Fierce Deity Sheaths (`#1606`) | Done locally | Ported custom-model sheath display-list hook for Fierce Deity form. |
| Custom Ocarina Controls (`#1598`) | Done locally | Ported menu/runtime hook and widened Android/libultraship controller masks to `uint32_t`; Android uses upper custom bits to avoid virtual-stick mask conflicts. |
| Port Extraction Flow / ImGui scaling / file permission check (`#1709`) | Partially ported | Android already had storage-permission checks and modal helpers; current batch ports the major-version-only ROM archive regeneration behavior plus live Android menu-scale sizing. |
| Transformation Mask Hints | Verified present | Rando option/menu entry, `FindMultiItemPlacement`, and South Clock Town sign hook are already present locally. |
| NNL cow blacklist (`#1628`) | Verified present | Nearly No Logic already keeps Epona's Song off cow checks. |
| RI_TRAP lesser item type (`#1704`) | Verified present | Knockoff Item is already `RITYPE_LESSER`. |
| Moon crash edge-case hooks (`#1691`) | Verified present | Android already calls `GameInteractor_ExecuteBeforeMoonCrashSaveReset()` for the Oath-without-Giants edge case. |
| Stale skeleton cache on alt toggle (`#1689`) | Verified present | `SkeletonPatcher::RegisterSkeleton` updates existing entries and unregister removes duplicate stale entries. |
| Chateau state through Song of Time (`#1619`) | Verified present | Cycle option/menu entry and week-event restoration are already present. |
| Extra Powder Kegs (`#1600`) | Verified present | Source hooks, HUD ammo behavior, and Android menu entry are already present. |
| Better Owl Warp Menu (`#1572`) | Verified present | Better owl warp menu source, pause integration, and menu entry are already present. |
| Picto Box on C-Up (`#1558`) | Verified present | Source hook, vanilla behavior bridge, and menu entry are already present. |
| Always Show Shrine of Truth Feathers (`#1594`) | Verified present | Source hook, vanilla behavior bridge, and menu entry are already present. |
| Bank Reward Hint (`#1595`) | Verified present | Bank sign hint behavior, rando option, and menu entry are already present. |
| Tycoon's Wallet (`#1597`) | Verified present | Rando item, draw/conversion/give/remove behavior, tracker setting, and wallet HUD handling are already present. |
| Gossip Stone hint weights (`#1567`) | Verified present | Gossip hint strength option and weighted item-type handling are already present. |
| Flippers icon (`#1586`) | Verified present | Flippers texture and draw-item wiring are already present. |
| Chu drops flagged-bomb fix (`#1587`) | Verified present | Chu drop replacement masks actor params before comparing bomb-drop IDs. |
| Arrow buyback full-price fix (`#1555`) | Done locally | Arrows now ignore Half Price buyback mode and always sell at full value, matching upstream/MMR behavior. |
| Red Potion and Gold Dust refill first-attempt fix (`#1565`) | Verified present | Rando actor behavior already grants refill item gets on first attempt when an empty bottle is available. |
| Blast Mask cooldown slider (`#1559`) | Cleaned up locally | Slider hook/menu were present; removed stale hidden `NoBlastMaskCooldown` registration and updated search metadata. |
| Rando seed file-select icons (`#1540`) | Verified present | File-select rando seed hash icon drawing is already present locally. |
| Curiosity Shop Seahorse refill requirements (`#1518`) | Verified present | Seahorse availability uses Great Bay/Zora/Pictograph/Swim requirements and Ammo Buyback handles Seahorse sale correctly. |
| Soaring hint item fix (`#1530`) | Verified present | Song of Soaring stone hint already looks up `RI_SONG_SOARING` instead of Hookshot. |
| Smithy day logic and progressive time obtainability (`#1528`, `#1529`) | Verified present | Smithy access is day-gated through `RE_ACCESS_SMITHY`; Progressive Time cannot be obtained from already-obtained checks or after all half-days are owned. |
| Giant Bee enemy-drop soul logic (`#1515`) | Verified present | Beehive Giant Bee checks already require both projectile access and `CanKillEnemy(ACTOR_EN_BEE)`. |
| Bad Bat tree heart-piece soul logic (`#1503`) | Done locally | Road to Southern Swamp heart piece now uses `CanKillEnemy(ACTOR_EN_BAT)` directly, matching upstream soul-aware logic. |
| Triforce Hunt tracker count guard (`#1520`) | Not applicable locally | Upstream patch targets the newer item-count tracker path; Android's current item tracker does not draw rando item counts through that code path. |
| Spider House hint replacements (`#1501`) | Verified present | Swamp/Ocean Spider House hints already use the correct `{{item}}` replacement and item article formatting. |
| Time logic and glitchless generator fixes (`#1500`, `#1506`) | Verified present | Glitchless junk replacement handling/logging, excluded-check refresh, and Milk Road/Romani Ranch time gates are already present. |
| Gorman Track tree shuffle removal (`#1511`) | Verified present | Gorman Track trees are already absent from tree actor mapping, region checks, static check data, and rando check IDs. |
| Stone Tower Temple water logic (`#1512`) | Verified present | Water-room checks are split into underwater region logic and require Swim/Light/Ice arrow conditions as upstream. |
| Rando menu metrics/trap text polish (`#1508`) | Verified present | Balance status, non-junk item pool display, excluded-check refresh, and trap tooltip text are already present; Triforce tracker portion is not applicable to Android's current tracker. |
| Granny story clock-shuffle softlock fix (`#1479`) | Done locally | Android already had the vanilla-behavior enum; current batch ports the missing Granny actor hook and redirects the day increment to the next owned half-day. |
| Format strings before printing (`#1644`) | Verified present | ImGui dynamic text/tooltip calls audited in local paths already use explicit format strings. |
| Tatl Great Bay interrupt exclusion (`#1569`) | Verified present | Local code already excludes the Great Bay Termina Field cutscene IDs. |
| Music Box House player freeze (`#1563`) | Verified present | Local code already freezes player movement after skipping the Gibdo dad burst-out cutscene. |
| Tingle Always in Clock Town (`#1690`) | Verified present | Source/menu hook already exists in `Enhancements/Cycle`. |
| Timer mode restart fix (`#1678`) | Verified present | Android menu already uses `gDisplayOverlay.Mode` with overlay visibility callback. |
| Skip Owl Statue and Laundry Pool Bell cutscenes (`#1671`, `#1672`) | Verified present | `SkipOnePointCutscenes` already covers `ACTOR_OBJ_WARPSTONE` and `ACTOR_EN_CHA`. |
| Sakon's Hideout/Kafei cutscene skips (`#1649`, `#1650`, `#1663`) | Verified present | Android already has the consolidated `SkipSakonsHideoutCutscenes` hook via `VB_START_CUTSCENE`. |
| Bomb Arrow HUD visual update (`#1677`) | Verified present | Android already displays arrow count when out of bombs and fades the bomb overlay. |
| Deku Butler animation fix (`#1442`) | Verified present | Source/menu hook and vanilla behavior bridge are already present. |
| Boat Archery custom-health dialogue (`#1634`) | Verified present | Android already rewrites the failure text for custom hit counts. |
| Persistent Owl Save warning cleanup (`#1632`) | Verified present | Android already removes the owl-save reset warning when persistent owl saves are enabled. |
| Disable SFX replacement (`#1679`) | Verified Android-handled | No SFX replacement lookup is active locally; no source change needed. |
| Android release Node/action updates | Ported | Workflow currently passes with action versions used in release `26700931957`. |

## Active Batch

| Item | Status | Risk | Notes |
| --- | --- | --- | --- |
| Clear lost rupee/ammo warnings when end-of-cycle preservation options restore those values | Done locally | Low | Upstream split this into per-CVar hooks; Android keeps old grouped registration, so port only behavior. |
| Extended Projectile Interaction Distance | Done locally | Medium | Source ported from upstream and adapted for Android branch collider names/EnIshi fields. |
| Disable SFX replacement | Verified | Low | Android already avoids the replacement lookup this upstream fix disabled. |
| Write parity tracker | Done locally | Low | This file. |
| Run native compile check | Passed | Medium | `:app:externalNativeBuildDebug` passes; use GitHub release workflow for final APK confidence. |
| Song Items plus softlock fix | Done locally | Medium | Source/menu/actor hooks ported; native compile check passes. |
| Skip Soaring cutscene and Skip Enemy Cutscenes menu exposure | Done locally | Medium | Skip Soaring source is ported; Skip Enemy source was already present; native compile check passes. |
| Sun's Song/Faster Song Playback ocarina end fix | Done locally | Low | Small upstream gameplay guard ported; native compile check passes. |
| Hyrule Warriors Styled Link source hook | Done locally | Low | Menu entry was already visible; missing source registration is now ported; native compile check passes. |
| Third save file slot toggle | Done locally | Medium | Menu entry was already visible; file-select/copy/erase CVar handling is ported; native compile check passes. |
| Fix Console Crashes source hooks | Done locally | Medium | Menu entry was visible; Android was missing the upstream reset helper and several source guard callsites; native compile check passes. |
| Fierce Deity Sheaths | Done locally | Low | Self-registering custom-model hook ported from upstream; native compile check passes. |
| Custom Ocarina Controls | Done locally | Medium | Upstream feature ported with Android-specific `uint32_t` button masks above the virtual-stick range; native compile check passes. |
| Extraction flow and Android menu-scale cleanup | Done locally | Medium | ROM archive compatibility now checks major version only, ROM picker cancel no longer shows an extra error, and Android menu scale applies widget spacing live. |
| Rando/timesaver presence audit | Done locally | Low | Confirmed Transformation Mask Hints, NNL cow blacklist, RI_TRAP lesser type, Shrine feathers, Picto C-Up, Extra Powder Kegs, Better Owl Warp, Bank Reward Hint, Tycoon's Wallet, Gossip Stone weights, Flippers icon, and Chu drop fix are already present. |
| Arrow buyback full-price fix | Done locally | Low | Small upstream gameplay fix; native compile check passes. |
| Refill/file-select/mask polish audit | Done locally | Low | Red Potion/Gold Dust refill and file-select rando icons verified; stale Blast Mask cooldown search/runtime metadata cleaned up; native compile check passes. |
| Rando logic microfix audit | Done locally | Low | Curiosity Shop Seahorse requirements, Soaring hint, Smithy day gate, Progressive Time obtainability, and Giant Bee soul logic verified; Bad Bat tree heart-piece logic adjusted locally; native compile check passes. |
| Older rando logic/menu audit | Done locally | Low | Verified Spider House hint replacements, time/glitchless fixes, Gorman Track tree removal, Stone Tower Temple water logic, and rando menu metrics/trap text polish are already present. |
| Clock Shuffle Granny story redirect | Done locally | Medium | Ports the missing source hook from upstream `#1479`; native compile check passes. |
| Starting item and Pause Owl Warp rando audit | Done locally | Low | Verified Romani Mask Milk Bar logic, computed starting-item pool removal, and legacy/empty starting-items handling are already present; ported upstream `#1467` Pause Owl Warp gating for ocarina/buttons/song restrictions. |
| Pre-playstate item tracker display (`#1477`) | Done locally | Low | Android's tracker is older than upstream's rewritten item tracker, so this ports the local equivalent: render faded tracker contents without a `PlayState` and avoid gameplay-only ammo/pause reads until a save is loaded. |
| Older gameplay/rando microfix audit (`#1450`, `#1457`, `#1461`, `#1436`, `#1416`, `#1418`, `#1409`, `#1396`, `#1394`) | Done locally | Low | Auto Bank notification throttling, enemy soul draw segment fixes, swim grant, Deku Search Ball labels, skateblock push-only speed, Beaver race skip, cow softlock, and Swordsman pot logic were already present; ported the missing EnOt reset callback. |
| Older cutscene/grass logic audit (`#1387`, `#1384`, `#1377`, `#1374`, `#1369`) | Verified present | Low | Mayor's Office skip preserves Dotour's notebook entry, Hungry Goron forced dialogue skip, Woodfall repeat clear-cutscene skip, chest-grotto grass actor-list mapping, and Gorman Track alien gating are already present in Android. |
| Actor reset and shuffled drop visibility audit (`#1371`, `#1350`, `#1363`, `#1356`) | Done locally | Low | Grass/crate shuffled-state checks and Day 2 rain bean logic were already present; wired Boss03 and Dinolfos reset callbacks and extended Invadepoh reset cleanup for static alien/event pointers. |
| Older rendering/cutscene microfix audit (`#1316`, `#1315`, `#1280`, `#1288`, `#1265`) | Done locally | Low | Bomb Shop owner draw restriction, telescope BGM guard, and Rosa Sisters Item_Give skip were already present; ported lens actor visibility and Skulltula flag-mask fixes. |

## Next Candidate Batch

These are good 3-4 item batches for the next GitHub build.

| Priority | Upstream area | Status | Why next |
| --- | --- | --- | --- |
| 1 | Basic surround sound support (`#1516`) | Needs libultraship audio review | Upstream depends on newer LUS audio-channel plumbing; Android currently opens SDL audio as stereo and needs a careful libultraship adaptation. |
| 2 | Port Extraction Flow, ImGui scaling, file permission check from SoH (`#1709`) | Partially ported | Keep deeper extraction-progress/window-bootstrap pieces under review; Android now has the low-risk archive-version and menu-scale portions. |
| 3 | Cosmetics editor modernization (`#1633`, `#1617`) | Deferred | Large UI and rendering surface; likely needs Android layout/performance review before porting. |
| 4 | ClockShuffle cleanup / Keiichi rando drift (`#1546`, `develop-keiichi`) | Needs review | Large rando logic diff; audit separately from gameplay enhancement batches. |
| 5 | Surround/cosmetics/rando tracker cleanup | In progress | Continue narrowing remaining upstream-only deltas before choosing the next GitHub build batch. |

## Larger Backlog

| Area | Status | Notes |
| --- | --- | --- |
| Audio editor / custom sequences | Deferred | Many upstream files are absent locally. Needs libultraship compatibility review. |
| Basic surround sound support | Needs review | Game-side setting changes are straightforward, but Android/libultraship needs channel negotiation/conversion before enabling 5.1 output safely. |
| Cosmetics editor UI | Deferred | Large UI addition; likely desktop-oriented. Needs Android menu/layout review. |
| Song Items | Done locally | Ported source, D-pad behavior, menu exposure, and softlock fix together. |
| Upstream BenGui modernization | Deferred | Broad changes; avoid mixing with gameplay fixes. |
| Randomizer feature drift | Deferred | Large surface area. Keep separate from small enhancement parity batches. |

## Audit Commands

```sh
git fetch upstream
git fetch jamer
git log --oneline --ancestry-path jamer/develop..upstream/develop
git diff --name-status refs/heads/android..upstream/develop -- mm/2s2h/Enhancements mm/2s2h/BenGui mm/src mm/include
```
