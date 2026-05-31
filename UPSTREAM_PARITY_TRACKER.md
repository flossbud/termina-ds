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
| Android release Node/action updates | Ported | Workflow currently passes with action versions used in release `26700931957`. |

## Active Batch

| Item | Status | Risk | Notes |
| --- | --- | --- | --- |
| Clear lost rupee/ammo warnings when end-of-cycle preservation options restore those values | Done locally | Low | Upstream split this into per-CVar hooks; Android keeps old grouped registration, so port only behavior. |
| Write parity tracker | Done locally | Low | This file. |
| Run native compile check | Passed | Medium | `:app:externalNativeBuildDebug` passes; use GitHub release workflow for final APK confidence. |

## Next Candidate Batch

These are good 3-4 item batches for the next GitHub build.

| Priority | Upstream area | Status | Why next |
| --- | --- | --- | --- |
| 1 | Song Items (`#1566`) plus softlock fix (`#1696`) | Deferred larger port | Android menu entry exists but is hidden with `HideUnsupportedAndroidOption`; upstream source is absent locally. Port as a full feature, not a small hook fix. |
| 2 | Extended Projectile Interaction Distance (`#1681`) | Needs audit | Upstream adds a dedicated enhancement source file and menu entries. Confirm whether Android exposes it and whether source exists before porting. |
| 3 | Disable SFX replacement (`#1679`) | Needs Android/libultraship review | Small code diff, but it touches audio replacement behavior and upstream custom audio editor pieces that Android may not fully carry. |
| 4 | Port Extraction Flow, ImGui scaling, file permission check from SoH (`#1709`) | Android-specific review | Large, high-value, but overlaps Android setup/extraction and should be adapted carefully rather than cherry-picked. |

## Larger Backlog

| Area | Status | Notes |
| --- | --- | --- |
| Audio editor / custom sequences | Deferred | Many upstream files are absent locally. Needs libultraship compatibility review. |
| Cosmetics editor UI | Deferred | Large UI addition; likely desktop-oriented. Needs Android menu/layout review. |
| Song Items | Deferred | Hidden unsupported on Android today. Port source, registration, D-pad behavior, and softlock fix together. |
| Upstream BenGui modernization | Deferred | Broad changes; avoid mixing with gameplay fixes. |
| Randomizer feature drift | Deferred | Large surface area. Keep separate from small enhancement parity batches. |

## Audit Commands

```sh
git fetch upstream
git fetch jamer
git log --oneline --ancestry-path jamer/develop..upstream/develop
git diff --name-status refs/heads/android..upstream/develop -- mm/2s2h/Enhancements mm/2s2h/BenGui mm/src mm/include
```
