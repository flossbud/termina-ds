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

## Next Candidate Batch

These are good 3-4 item batches for the next GitHub build.

| Priority | Upstream area | Status | Why next |
| --- | --- | --- | --- |
| 1 | Song Items (`#1566`) plus softlock fix (`#1696`) | Done locally | Android menu entry is now visible; upstream source and gossip-stone cleanup hooks are ported pending GitHub build validation. |
| 2 | Extended Projectile Interaction Distance (`#1681`) | Done locally | Android already exposed the menu item but hid it as unsupported; source is now ported and adapted for local collider/actor field names. |
| 3 | Disable SFX replacement (`#1679`) | Verified Android-handled | Local SFX path does not call SFX replacement, and sequence-player replacement lookup is already commented out. No audio-editor dependency needed. |
| 4 | Port Extraction Flow, ImGui scaling, file permission check from SoH (`#1709`) | Android-specific review | Large, high-value, but overlaps Android setup/extraction and should be adapted carefully rather than cherry-picked. |

## Larger Backlog

| Area | Status | Notes |
| --- | --- | --- |
| Audio editor / custom sequences | Deferred | Many upstream files are absent locally. Needs libultraship compatibility review. |
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
