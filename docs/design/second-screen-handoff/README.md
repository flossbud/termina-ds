# Handoff: Termina DS — dual-screen gameplay HUD, pause menu & graphics options

## Overview

A dual-screen handheld game UI (an N64-era action-adventure running on a fictional "Termina DS" handheld). The design covers two physical displays that are always visible at once:

- **Top screen — 1920 × 1080.** The game render, with the on-screen quick-item HUD (D-pad ring + face-button cluster). When the player pauses, the render is veiled and the top screen becomes an ornamental "PAUSED" plate; when the inventory is open on the bottom screen, the top screen becomes an **item showcase + assignment overview**.
- **Bottom screen — 1240 × 1080 (touch).** During play: the area map with a slim vitals bar and a MAP / ITEMS / MASKS nav. While paused: the pause root menu and four subscreens — Inventory, Map, Song of Time, and Options (Settings / Enhancements, each split by category, with the Graphics category fully specified).

Everything is one interactive prototype driven by one state object; the two screens are rendered side by side so the relationship between them is legible.

## About the Design Files

The files in this bundle are **design references authored in HTML** — a prototype that shows intended look, proportion, hierarchy and behavior. They are **not production code to copy**. The task is to **recreate these designs inside the target codebase's existing environment** (game UI layer, React/Vue/Swift/UE/Unity widget system, etc.) using its established patterns, layout primitives and asset pipeline. If no UI environment exists yet, pick the most appropriate one for the project and implement the designs there.

`Pause Screen.dc.html` is authored in a small HTML component runtime (`support.js`, `<x-dc>` template + a logic class). Treat the runtime as an implementation detail of the prototype: read the markup for structure/values and the logic class for state and derived values, then reimplement idiomatically.

## Fidelity

**High-fidelity (hifi).** Colors, typography, spacing, geometry, motion timings and copy are final and should be reproduced precisely. Two intentional placeholders remain:

1. The top-screen game render and the map are **screenshot stand-ins** (`uploads/*.png`). Real renders replace them.
2. In the top-screen assign overview, the selected item is drawn as its 2D inventory icon with the caption `ICON PLACEHOLDER · 3D RENDER LATER`. A rotating 3D item render is intended to replace it in the same 424 × 424 box.

---

## Design tokens

### Color

| Token | Hex / value | Used for |
|---|---|---|
| Page shell (outside the screens) | `#111009` | Prototype backdrop only |
| Screen background | `#000` | Both screens |
| Ink / primary lavender | `#efe6ff` | Active tab labels, primary values |
| Ink 2 | `#e2d6f6` | Setting row labels, clock |
| Ink 3 | `#d7c6f4` | Subscreen titles |
| Ink muted | `#a58ed0` | Chevrons, back arrow, DAY label |
| Accent purple | `#b48ce8` | Diamonds, rules, glows (the system accent) |
| Accent purple light | `#cbb0f2` / `#c9a2ff` | Underlines, chips, clock hand |
| Purple hairline | `rgba(180,140,232,.11–.14)` | Row rules, dividers |
| Purple hairline strong | `rgba(180,140,232,.45–.5)` | Selected row rule, diamond dividers |
| Gold | `#e0bd66` | Numeric values, item names, active segmented option underline, selection rim |
| Gold light | `#f0d488` | Active option label, ASSIGN action |
| Gold dim | `#c9b17a` / `#8a7647` | Reload-bar text, "loses" chip |
| Text dim | `#6a5f85` | Row descriptions |
| Text dimmer | `#5d5570` / `#544d69` | Captions, inactive tabs |
| Text dimmest | `#3f3950` | Footer hints |
| Heart red | `#ff4d5e` (full) / `#17161c` fill + `#3b3846` stroke (empty) | Vitals hearts |
| Magic green | `#4ade80` on track `#1e1c24` | Magic rail |
| Rupee green | `#5ec46f` | Rupee diamond |
| Count green | `#c6ff6a` | Ammo/quantity numerals |
| Warning amber border | `rgba(224,189,102,.34–.6)` | Reload bar |
| Button gold rim (in-game HUD) | `#cfa53d` with `inset 0 0 0 2px #f0dc9a` | Top-screen item buttons |
| A-button blue | `#4478c4`–`#dceeff` radial, border `#3d6db3` | Fixed A button |
| B-button green | `#3a7d35`–`#d7f3bd` radial, border `#3a7d35` | Fixed B button |

### Typography

| Role | Font | Size / weight / tracking |
|---|---|---|
| Wordmark "PAUSED" | Cinzel 800 | 176px, `letter-spacing:.18em`, `line-height:.95` |
| Pause menu rows | Cinzel 700 | 42px, `letter-spacing:7px` |
| Screen/tab labels (SETTINGS, ITEMS, MAP) | Cinzel 700 | 27px (subscreen tabs) / 23px (gameplay nav), `letter-spacing:6–7px` |
| Subscreen title | Cinzel 700 | 30px, `letter-spacing:5px` |
| Setting row label | Cinzel 700 | 30px, `letter-spacing:4px` |
| Numeric value readout | Cinzel 700 | 34px, `letter-spacing:1px`, gold |
| Segmented option | Cinzel 700 | 23px (numeric) / 21px (word), `letter-spacing:2–3px` |
| Item name (inventory + showcase) | Cinzel 700 | 32px / 40px, `letter-spacing:3px`, gold |
| Row description | Chivo Mono 400 | 13px, `letter-spacing:2.5px`, uppercase |
| Captions / hints / chips | Chivo Mono 500–700 | 11–15px, `letter-spacing:2–5px` |
| Clock + counters | Chivo Mono 700 | 17–18px |
| Body prose (Song of Time, item description) | Barlow 400 | 18px / 27px, `line-height:1.5–1.6`, `text-wrap:pretty` |
| In-game button glyphs | Barlow 800 | 13–18px |

Fonts: `Cinzel` 600/700/800, `Barlow` 400/500/600/700/800, `Chivo Mono` 500/700 (Google Fonts).

### Geometry & style rules

- **The classic vocabulary:** no rounded cards. Rows are separated by **1px hairlines**; controls are **underlined labels** (2px gold underline = active, transparent = inactive); the only radii are `999px` on true circles (clock dials, in-game buttons, badge dots) and `2px` on rotated squares (diamonds).
- **Selection is marked three ways, never with a box:** a breathing gold diamond at the left of the label, the row's top hairline brightening to `rgba(180,140,232,.46)`, and a soft wash `radial-gradient(72% 150% at 16% 50%, rgba(180,140,232,.14), transparent 74%)`.
- **Diamonds** are `9–16px` squares with `border-radius:2px` and `transform:rotate(45deg)`.
- **Sliders** are 300 × 2px hairline rails; fill `linear-gradient(90deg, rgba(180,140,232,.55), #cbb0f2)`; knob is a 16px diamond (`margin:-8px 0 0 -8px`) with `box-shadow:0 0 14px rgba(180,140,232,.55)`; the default value is marked by a 1px vertical tick (`top:-9px;bottom:-9px`) at the value's percentage position.
- **Steppers** are borderless Cinzel chevrons `‹` `›`, 50 × 54px hit area, `#a58ed0` → `#efe6ff` on hover.
- **Checkboxes** are 46 × 46px hairline squares (no radius) containing a 15px gold diamond when checked.
- **Touch targets:** every interactive element is ≥ 42px tall; grid cells are ~186 × 180px; segmented options 58–60px tall.
- **Spacing scale in use:** 4, 6, 8, 10, 12, 14, 16, 18, 22, 24, 26, 28, 34, 40, 44px. Screen gutters are 44px (subscreens) / 30px (gameplay bottom screen) / 40px (subscreen header + footer).

### Motion

All keyframes are in the prototype's `<style>` block.

| Name | Definition | Applied to |
|---|---|---|
| `pzIn` | opacity 0→1, `.28–.42s ease both` | Veil, subscreen and menu entry |
| `pzWordIn` | letter-spacing `.42em→.18em`, blur 9px→0, opacity 0→1, `.8s .06s cubic-bezier(.2,.8,.2,1)` | PAUSED wordmark |
| `pzSweep` | `background-position -70%→170%`, `5.2s linear infinite` | Wordmark gradient sheen |
| `pzGlow` | drop-shadow 18px/.30 → 44px/.62 → 18px, `4.4s ease-in-out infinite` | Wordmark halo |
| `pzRule` | width 0→420px + opacity, `.9s .3s cubic-bezier(.2,.8,.2,1)` | Hairline under the wordmark |
| `pzRise` | translateY 14px→0 + opacity, `.4–.6s ease both`, staggered `.1 / .2 / .34 / .5s` | Veil elements, plate, showcase icon |
| `pzBreathe` | opacity .4→1→.4, `2.1s ease-in-out infinite` | Every selection diamond, map position marker |
| `pzHand` | rotate 26.6°→29.4°→26.6°, `5.4s ease-in-out infinite` | Clock dial hand |
| `pzMenuIn` | translateY 10px→0 + opacity, `.4s ease both` | Pause root menu list |
| Knob / toggle transitions | `160ms ease` | Slider knob, switch travel |

---

## Screens / views

### 1. Top screen — gameplay HUD (1920 × 1080)

**Purpose:** the game itself, plus the always-visible quick-item HUD.

- Game render: `<img>` filling the screen, `object-fit:cover`, `transform:scale(1.32)` from origin `50% 54%` (crops the source screenshot to a playable framing).
- **D-pad quick-item ring** — bottom-left, `left:32px; bottom:40px`, 224 × 224 box. `assets/dpad.png` at `opacity:.92`, `drop-shadow(6px 9px 8px rgba(0,0,0,.45))`. Four 82 × 82 item icons pinned to the arms: up `top:-6px` (Hookshot), left `left:-10px` (Lens of Truth), right `right:-10px` (Fierce Deity's Mask), down `bottom:-6px` (Bottle/Milk); each `drop-shadow(0 3px 4px rgba(0,0,0,.6))`.
- **Face-button cluster** — bottom-right, `right:20px; bottom:18px`, 310 × 250 box:
  - **X** (`left:90px; top:36px`) 76 × 76 circle, `border:5px solid #cfa53d`, fill `radial-gradient(circle at 50% 30%, #2c313d, #0f1218 76%)`, `box-shadow: inset 0 0 0 2px #f0dc9a, 0 4px 10px rgba(0,0,0,.55)`; icon at 60%; count `20` in Chivo Mono 16/800 `#c6ff6a` with a 1px black outline via four text-shadows.
  - **Y** (`left:26px; top:100px`) same treatment, Bow, count `30`.
  - **R** (`left:204px; top:14px`) 84 × 44 pill, `border-radius:8px 20px 20px 8px`, `border:3px solid #cfa53d`, Ocarina icon 32px.
  - **A** (`left:156px; top:101px`) 74 × 74 blue gloss circle, label `Grab` Barlow 800/18.
  - **B** (`left:90px; top:166px`) 76 × 76 green gloss circle, Razor Sword icon at 58%.

### 2. Top screen — pause veil

Overlays the render at `z-index:5`: `backdrop-filter: blur(7px) saturate(.42) brightness(.34)` plus `radial-gradient(105% 82% at 50% 44%, rgba(26,14,44,.42), rgba(0,0,0,.86) 76%)`.

Centered column (each element rises in with staggered `pzRise`):
1. Ornament: two 120 × 1px gradient rules flanking a 44px circle clock (1.5px `rgba(180,140,232,.55)` border, 2 × 15px hand `#cbb0f2` animating with `pzHand`, 6px hub).
2. **PAUSED** — Cinzel 800/176, `letter-spacing:.18em`, gradient-clipped text with `pzSweep` + `pzWordIn` + `pzGlow`. Three palettes, selectable via the `pauseEffect` prop:
   - *Shimmer* (default): `#5b3f92 → #8f68c9 24% → #f6ecff 46% → #b48ce8 58% → #5b3f92`
   - *Moonlight*: `#6d7295 → #b9c2e4 26% → #ffffff 46% → #c3cbe8 58% → #6d7295`
   - *Ember*: `#7a4a17 → #c8912f 24% → #fff4d2 46% → #e0bd66 58% → #7a4a17`
3. Subtitle `THE CLOCK HOLDS ITS BREATH` — Chivo Mono 21/500, `letter-spacing:9px`, `#9d8dbe`.
4. 1px rule, `margin-top:40px`, animates to 420px wide (`pzRule`).
5. `CONTINUE ON THE BOTTOM SCREEN` — Barlow 22/600, `letter-spacing:2.5px`, `#c9bfe0`, preceded by a breathing 9px purple diamond.

Corners: `DAY 1 | 60 H LEFT` at `left:34px; top:30px` and `TERMINA FIELD` at `right:34px; top:30px` — Chivo Mono 15, `letter-spacing:5px/4px`, `#7d6f9c`, separated by a 1 × 15px rule.

### 3. Top screen — assign overview (shown while the Inventory subscreen is open)

Replaces the wordmark (the wordmark is hidden when `view === 'inventory'`).

- **Showcase** — box `left:370px; top:0; width:1180px; height:770px`, centered column. Tab caption (`ITEMS` / `MASKS`) Chivo Mono 15, `letter-spacing:8px`, `#7d6f9c`. Item icon 424 × 424 with `drop-shadow(0 20px 24px rgba(0,0,0,.85)) drop-shadow(0 0 42px rgba(180,140,232,.26))`, over a 720px purple radial. Contact shadow: 320 × 18px radial ellipse. Caption `ICON PLACEHOLDER · 3D RENDER LATER` Chivo Mono 12, `letter-spacing:4px`, `#4e4664`.
- **Description plate** — `left:440px; bottom:74px`, 1040 wide, `min-height:236px`, `border-radius:22px`, `border:1px solid rgba(180,140,232,.26)`, `background:linear-gradient(180deg, rgba(23,16,40,.9), rgba(8,7,12,.92))`, `box-shadow:0 20px 54px rgba(0,0,0,.62), inset 0 1px 0 rgba(255,255,255,.05)`, padding `26px 30px 30px`. Contains: item name (Cinzel 40/700 `#e0bd66`, halo `0 0 26px rgba(224,189,102,.3)`), `×count` (Chivo Mono 24/700 `#c6ff6a`), an assignment chip on the right (`ON X BUTTON` / `NOT ASSIGNED`, 1px `rgba(180,140,232,.34)`, radius 10, Chivo Mono 14 `letter-spacing:3px` `#c9a2ff`), description (Barlow 27/400, `line-height:1.5`, `#a79cbd`), and the hint `TAP A SLOT TO ASSIGN`.
- **Assignment clusters** — a row at `left:56px; right:56px; bottom:30px`, `justify-content:space-between`:
  - *D-PAD · QUICK ITEMS* (`zoom:.7`, origin `0 100%`): 384 × 384 box, `dpad.png` at 286px, four 124px circular slots at up `(192,68)`, left `(68,192)`, right `(316,192)`, down `(192,316)` (center-anchored via `margin:-62px 0 0 -62px`). Slot chrome: `border:5px solid #cfa53d` when filled / `5px dashed rgba(207,165,61,.5)` when empty; when the slot already holds the selected item the rim becomes `#f6e6ae` with `box-shadow:0 0 34px rgba(224,189,102,.55)`. Each slot carries a 34px key badge (`▲ ◄ ► ▼`, `#1a1408` fill, `2px #e7c35a` border, `#ffdf7a` text) and a green count numeral.
  - *BUTTONS* (`zoom:.7`, origin `100% 100%`): 452 × 384 box — R pill (168 × 80 at `312,26`), X (132px at `186,66`), Y (132px at `76,196`), A (126px at `314,196`, `opacity:.62`, locked), B (130px at `186,322`, `opacity:.62`, locked); caption `A AND B ARE FIXED · ACTION AND SWORD`.

### 4. Bottom screen — gameplay (1240 × 1080)

**Purpose:** the map is the default second-screen view; the nav switches to inventory.

- **Vitals bar** — `height:64px`, padding `0 30px`, single flex row, `gap:18px`:
  - Hearts: 10 × 18px heart SVGs, `gap:3px`. Full `#ff4d5e`; empty `fill:#17161c; stroke:#3b3846; stroke-width:1.4`.
  - Magic rail: 130 × 5px, `border-radius:999px`, track `#1e1c24`, fill `#4ade80` at `{magicPct}%`, `margin-left:12px`.
  - Rupees: 11px green diamond + Chivo Mono 17/700 `#eaeaea`, `margin-left:14px`.
  - Right group: `DAY 1` (Chivo Mono 13/700, `letter-spacing:4px`, `#a58ed0`), `7:40` (Chivo Mono 18/700 `#f0eef5`) with `AM` at 11px `#6f6288`, and the countdown chip `60 H` — 1px `rgba(203,176,242,.45)`, `border-radius:6px`, padding `3px 10px`, Chivo Mono 13/700 `#cbb0f2`.
- **Map** — absolutely positioned `top:64px; bottom:104px; left:0; right:0`, `overflow:hidden`, image `object-fit:cover`. Full-bleed: **no vignette/mask** on this view.
- **Area label** — `left:30px; top:78px`, Cinzel 20/700, `letter-spacing:3px`, `#f0eef5`, `text-shadow:0 2px 10px rgba(0,0,0,.9)`.
- **Screen nav** — bottom bar `height:104px`, centered, `gap:56px`: `MAP` · `ITEMS` · `MASKS`, Cinzel 23/700, `letter-spacing:7px`, 46px tall, `border-bottom:2px solid`. Active `#efe6ff` + `#c9a2ff` underline; inactive `#544d69` + transparent underline. ITEMS/MASKS open the paused Inventory subscreen on that tab.

### 5. Bottom screen — pause root menu

- Clock line, `top:34px`, centered: `DAY 1 · 7:40 AM` | `60 H LEFT`, Chivo Mono 13, `letter-spacing:5px`, `#4f4763`, split by a 1 × 13px rule.
- Menu list: 760px wide, `gap:4px`, entry animation `pzMenuIn`. Each row is `min-height:106px`, centered, and holds the label flanked by two breathing 9px diamonds at `gap:24px`. Label Cinzel 42/700, `letter-spacing:7px`.
  - Selected: `#e7dcfa` with `text-shadow:0 0 26px rgba(180,140,232,.45)`, diamonds `#b48ce8`, and a Chivo Mono 14 `letter-spacing:4px` `#6f6288` sub-line beneath.
  - Unselected: `#6b6380`, diamonds transparent, no sub-line.
  - `SONG OF TIME` is the one warm row: selected `#f0d488` / `0 0 26px rgba(224,189,102,.4)` / gold diamonds; unselected `#8a7647`.
  - Rows and sub-lines: `RESUME` → `TERMINA FIELD · AUTOSAVED 4 MIN AGO`; `INVENTORY` → `21 ITEMS · 12 MASKS`; `MAP` → `68% EXPLORED · 5 OF 10 OWL STATUES`; `SONG OF TIME` → `RETURN TO THE FIRST DAWN`; `OPTIONS` → `RESOLUTION · MSAA · FRAME RATE`.
- Footer hint, `bottom:38px`, centered: `Ⓐ SELECT · Ⓑ RESUME`, Chivo Mono 13, `letter-spacing:3px`, `#3f3950`.

### 6. Bottom screen — subscreen chrome (shared by all four subscreens)

- **Header** `height:96px`, padding `0 40px`, `gap:20px`, `border-bottom:1px solid rgba(180,140,232,.14)`: back chevron `‹` (46 × 52, borderless Cinzel 32, `#a58ed0` → `#efe6ff`), title (Cinzel 30/700, `letter-spacing:5px`, `#d7c6f4`), spacer, `PAUSED` chip (breathing 7px diamond + Chivo Mono 12/700 `letter-spacing:3px` `#c9a2ff`, 1px `rgba(180,140,232,.4)`, radius 7, padding `4px 11px`), `DAY 1 · 7:40 AM` (Chivo Mono 15, `#6f6288`), hours chip (`60 H`, 1px `#a06ee0`, radius 7).
- **Footer** `height:80px`, padding `0 40px`, `border-top:1px solid rgba(180,140,232,.14)`: contextual hint on the left (Chivo Mono 13, `letter-spacing:3px`, `#4f4763`) and `RESUME PLAY` on the right — 52px tall, borderless, `border-bottom:2px solid rgba(180,140,232,.45)`, Cinzel 19/700 `letter-spacing:5px` `#a99cc0`; hover `#efe6ff` + `#c9a2ff` underline; `white-space:nowrap`.
- Hints: Inventory `Ⓐ ASSIGN · Ⓑ BACK`; Options `↑ ↓ ROW · ◄ ► ADJUST · Ⓑ BACK`; others `Ⓑ BACK`.
- Titles: `INVENTORY`, `MAP`, `SONG OF TIME`, `OPTIONS`.

### 7. Inventory subscreen

- **Tabs** — centered row, padding `22px 44px 0`, `gap:24px`: `ITEMS` ◆ `MASKS` (Cinzel 27/700, `letter-spacing:6px`, 60px tall, `border-bottom:2px solid`), divider is an 8px `rgba(180,140,232,.45)` diamond. Active `#efe6ff` + `#c9a2ff`; inactive `#544d69` + transparent.
- **Count line** — centered, `padding-top:8px`, Chivo Mono 13, `letter-spacing:4px`, `#5d5570`: `21 OF 24 SLOTS FILLED` / `12 OF 24 MASKS RECOVERED`.
- **Grid** — `padding:16px 44px 0`, `grid-template-columns:repeat(6,1fr)`, `grid-template-rows:repeat(4,1fr)`, `gap:10px` (24 cells). Cell: square, `border-radius:0`, 1px border, icon centered at 62% with `drop-shadow(0 3px 5px rgba(0,0,0,.9))`, count `×N` bottom-right (Chivo Mono 14/700 `#c6ff6a`).
  - Selected + filled: border `#e0bd66`, background `radial-gradient(80% 100% at 50% 50%, rgba(224,189,102,.14), transparent 76%)`, `box-shadow:0 0 22px rgba(224,189,102,.26), inset 0 0 18px rgba(224,189,102,.08)`.
  - Filled: border `rgba(180,140,232,.17)`, transparent background, `cursor:pointer`.
  - Empty: border `rgba(255,255,255,.04)`, `cursor:default`, not selectable.
  - Hover on filled cells: `filter:brightness(1.25)`.
- **Detail band** — `height:136px`, `margin:14px 44px 0`, `border-top:1px solid rgba(180,140,232,.13)`, `gap:22px`: 76px hairline thumbnail square (icon 50px), name (Cinzel 32/700, `letter-spacing:3px`, `#e0bd66`) over a mono uppercase caption (`ASSIGN TO A FACE BUTTON OR D-PAD SLOT` / `WEAR TO TRANSFORM · ONE D-PAD QUICK SLOT`), and the action on the right: `Ⓐ ASSIGN` (masks: `WEAR`) — borderless, `border-bottom:2px solid #e0bd66`, Cinzel 22/700 `letter-spacing:5px` `#f0d488`, with a 26px circular `A` glyph.
- **Content:** 24 items (Ocarina of Time, Hero's Bow ×30, Fire/Ice/Light Arrow, Moon's Tear, Bomb ×20, Bombchu ×10, Deku Stick ×10, Deku Nut ×20, Magic Beans ×5, Letter to Kafei, Powder Keg ×1, Pictograph Box, Lens of Truth, Hookshot, Great Fairy's Sword, Pendant of Memories, Red Potion, Milk, Fairy, Fish, Gold Dust, Empty Bottle) and 24 masks (Postman's Hat → Fierce Deity's Mask; Couple's Mask, Gibdo Mask and Giant's Mask are the three unrecovered/empty cells).

### 8. Map subscreen

Map fills the body with a soft vignette: `mask: radial-gradient(108% 94% at 50% 48%, #000 54%, transparent 96%)`. Labels at `left:40px`: `TERMINA FIELD` (Cinzel 22/700, `letter-spacing:3px`, `#e4d9f2`, `top:26px`) and `CENTRAL · 68% EXPLORED · 5 OF 10 OWL STATUES` (Chivo Mono 14, `#a396bb`, `top:60px`). Zoom stack bottom-right (`right:34px; bottom:30px`, `gap:10px`): two 60px buttons `＋` / `－`, 1px `rgba(180,140,232,.45)`, radius 14, `rgba(10,8,16,.82)`, `#dcccf7`.

### 9. Song of Time subscreen

Centered column, `gap:22px`, `padding:0 120px`: ocarina icon 120px; prompt `Return to the first dawn?` (Cinzel 38/700, `letter-spacing:3px`, `#e0bd66`); body copy (Barlow 18, `line-height:1.6`, `#9b8f6f`, `max-width:720px`): "The cycle rewinds twelve hours of progress. Masks, heart pieces, and songs stay with you — rupees and consumables do not."; two chips (`KEEPS · MASKS · HEARTS · SONGS`, `LOSES · 218 RUPEES · AMMO`, radius 12, padding `14px 22px`); two 68px actions — `Ⓑ NOT YET` (1px `rgba(180,140,232,.28)`, `#0c0a11`, `#8a819c`) and `Ⓐ PLAY THE SONG` (1px `rgba(224,189,102,.65)`, `linear-gradient(180deg, rgba(224,189,102,.2), rgba(224,189,102,.05))`, `#f0d488`).

### 10. Options subscreen — two-axis sort

Options is sorted on **two axes**, so "Graphics" is one slice of each kind, not the whole screen:

- **Axis 1 — kind (tabs):** `SETTINGS` ◆ `ENHANCEMENTS` (same treatment as the inventory tabs: centered, Cinzel 27/700, `letter-spacing:6px`, 2px underline; active `#efe6ff`/`#c9a2ff`, inactive `#544d69`/transparent). Settings = how the game runs; Enhancements = things beyond the original hardware.
- **Axis 2 — category (chips):** centered row, `padding:14px 44px 6px`, `gap:28px`, Chivo Mono 14/700, `letter-spacing:4px`, 42px tall, `border-bottom:1px solid`; active `#f0d488` + `#e0bd66` underline, inactive `#635a7d` + transparent.
  - Settings: `GRAPHICS` · `AUDIO` · `CONTROLS` · `SYSTEM`
  - Enhancements: `GRAPHICS` · `GAMEPLAY` · `CAMERA` · `QUALITY OF LIFE`
- Non-Graphics categories intentionally show an **empty state** instead of filler rows: a `flex:1` panel, `margin:14px 40px 26px`, top+bottom hairlines, transparent fill, containing the category name (Cinzel 30/700, `letter-spacing:4px`, `#4f4763`) over `SETTINGS FOR AUDIO NOT DESIGNED YET` (Chivo Mono 14, `letter-spacing:3px`, `#3f3950`).

**Row anatomy (all option rows).** Container `flex:1; padding:4px 44px 18px; gap:0`. Each row is `flex:1` (≈145–170px tall), `display:flex; align-items:center; gap:26px; padding:0 22px`, with `border-top:1px solid` (selected `rgba(180,140,232,.46)`, otherwise `rgba(180,140,232,.11)`) and the selection wash described in *Design tokens*. Left column: breathing gold diamond + label (Cinzel 30/700, `letter-spacing:4px`, `#e2d6f6`) over the mono uppercase description (`padding-left:22px`). Right column: the control. Rows implemented as `<button>` must reset the UA border (`border:none` before `border-top`).

**Settings → Graphics (5 rows)**

| # | Control | Spec |
|---|---|---|
| 1 | **Internal Resolution** — slider | 50–200%, step 5, default **100%**; tick mark at 33.3% of the rail marks the default; readout `{n}%` in gold Cinzel 34. Description: `RENDERS ABOVE NATIVE, THEN DOWNSAMPLES · DEFAULT 100%` |
| 2 | **Anti-Aliasing (MSAA)** — segmented | `OFF · 1× · 2× · 3× · 4× · 5×`, six 78 × 58px options, default **2×**. The `MSAA` qualifier sits beside the label in Chivo Mono 15/500 `#7d6f9c`. Description: `SMOOTHS POLYGON EDGES · HIGHER LEVELS COST FILL RATE` |
| 3 | **Current FPS** — slider + chip | 20 → display maximum, step 5, default **60**; label carries a `MAX 60 HZ` chip. Grays out entirely when *Match Refresh Rate* is on: label `#5b5470`, chevrons `#38323f`, rail fill `rgba(120,112,140,.2)`, knob `#3b3648`, no knob glow, readout `#4a4232`, `cursor:not-allowed`, and the description swaps to `LOCKED BY MATCH REFRESH RATE` (otherwise `CAPS THE FRAME RATE BETWEEN 20 AND THE DISPLAY MAXIMUM`) |
| 4 | **Match Refresh Rate** — checkbox | Whole row is the hit target. Right side: state word (`ON` `#f0d488` / `OFF` `#5d5570`, Chivo Mono 14/700 `letter-spacing:3px`) + 46px hairline square holding a 15px gold diamond when on (border `#e0bd66` on, `rgba(180,140,232,.26)` off). Default **off**. Description: `FOLLOWS THE DISPLAY AT 60 HZ AND LOCKS THE FPS CAP` |
| 5 | **Texture Filter** — segmented, needs reload | `THREE-POINT · LINEAR · NONE`, default **Three-Point**; label carries a `NEEDS RELOAD` chip (1px `rgba(224,189,102,.4)`, `#c9b17a`). Description: `THREE-POINT MATCHES THE ORIGINAL HARDWARE BLUR` |

**Enhancements → Graphics (5 rows)**

| # | Control | Spec |
|---|---|---|
| 1 | **Widescreen** — segmented, needs reload | `4:3 · 16:9 · 21:9`, default **16:9**. Description: `WIDENS THE CAMERA FRUSTUM · SOME CUTSCENES STILL LETTERBOX` |
| 2 | **High-Res Texture Pack** — checkbox | Same checkbox pattern; label carries the pack chip `TERMINA HD · 412 MB`; default **off**. Description: `LOADS REPLACEMENT TEXTURES FROM THE PACK FOLDER` |
| 3 | **Anisotropic Filtering** — segmented | `OFF · 2× · 4× · 8× · 16×`, 82 × 58px options, default **4×**. Description: `SHARPENS GROUND TEXTURES VIEWED AT A SHALLOW ANGLE` |
| 4 | **Post Sharpening** — slider | 0–100%, step 5, default **25%**. Description: `CONTRAST-ADAPTIVE PASS AFTER UPSCALING · 0% DISABLES IT` |
| 5 | **Draw Distance Fog** — segmented | `ORIGINAL · EXTENDED · OFF`, default **Original**. Description: `EXTENDED PUSHES THE FOG WALL BACK PAST THE ORIGINAL CLIP PLANE` |

**Pending-reload bar.** Whenever a needs-reload setting differs from its applied value, a `flex:none; height:60px; margin:0 40px 22px` bar appears below the rows (shared by both tabs): top+bottom `1px rgba(224,189,102,.34)`, fill `linear-gradient(180deg, rgba(224,189,102,.12), rgba(224,189,102,.02))`, a breathing gold diamond, the message `TEXTURE FILTER AND WIDESCREEN CHANGED · RELOAD THE CORE TO APPLY` (Chivo Mono 14, `letter-spacing:2px`, `#c9b17a`, names only what is actually pending, joined with ` AND `), and a 42px `RELOAD NOW` action (`border-bottom:1px solid rgba(224,189,102,.6)`, `rgba(224,189,102,.1)`, Cinzel 15/700 `#f0d488`) that commits the pending values.

---

## Interactions & behavior

### Navigation model

| From | Action | Result |
|---|---|---|
| Gameplay (both screens) | `P` or `+` key, or the bottom-screen nav | Pause: top screen veils, bottom screen shows the root menu (`view:'menu'`) |
| Gameplay bottom nav | `ITEMS` / `MASKS` | Pause + open Inventory directly on that tab (root menu selection moves to `INVENTORY`) |
| Root menu | `↑` / `↓` | Move selection, wrapping (`(sel + d + n) % n`) |
| Root menu | `Enter` / `Space` / click a row | `RESUME` unpauses; every other row opens its subscreen and resets `pick` to 0 |
| Root menu | `Esc` / `Backspace` | Unpause |
| Subscreen | `Esc` / `Backspace`, back chevron | Return to the root menu |
| Any subscreen | `RESUME PLAY` | Unpause and reset to the root menu |
| Any state | `P` / `+` | Toggle pause and reset `view` to `menu` |

### Inventory assignment flow

1. Tapping a filled grid cell selects it (`pick`). The top screen immediately shows that item's showcase, name, count, description and current assignment.
2. Tapping any D-pad slot or face button **on the top screen** writes the selected item into that slot (`assign(slot)`), replacing whatever was there. `A` and `B` are permanently fixed (Action / Sword) and are rendered at `opacity:.62`, non-interactive.
3. Any slot already holding the selected item is highlighted with the `#f6e6ae` rim and gold glow, so the player can see where the item currently lives.
4. Empty inventory cells and empty slots are non-interactive; empty slots draw a dashed rim.
5. Assignments persist across pause/unpause and are the source of the gameplay HUD contents on the top screen.

### Options interaction

- Clicking a segmented option sets it and moves the row selection to that row.
- Chevrons step the sliders; keyboard `◄ ►` adjusts the selected row (resolution ±5, MSAA ±1, FPS ±5 clamped to the refresh rate, checkbox toggles, segmented values cycle), `↑ ↓` moves between the five rows (wrapping), `Enter`/`Space` behaves as `►`.
- FPS controls are inert while *Match Refresh Rate* is on; the displayed value becomes the refresh rate.
- Switching tab or category resets the row selection to 0.
- Hover on any control: `filter:brightness(1.2–1.3)` or a lift from the dim to the bright ink color; no layout shift anywhere.

### Motion behavior

Veil and subscreens fade in (`pzIn`); the wordmark performs its blur/tracking entrance once per pause; the menu list rises (`pzMenuIn`); selection diamonds and the map marker breathe continuously; the clock hand oscillates. Slider knobs and toggles animate at `160ms ease`. Nothing loops faster than 2.1s — the screen is calm while paused.

---

## State management

One state object drives both screens:

```
paused        : boolean            // false = gameplay, true = pause veil + menu
view          : 'menu' | 'inventory' | 'map' | 'song' | 'options'
sel           : int 0–4            // root-menu selection
invTab        : 'items' | 'masks'
pick          : int                // selected inventory cell index
slots         : { up, left, right, down, X, Y, R } -> { name, img, count }
optTab        : 'settings' | 'enhancements'
setCat        : 'Graphics' | 'Audio' | 'Controls' | 'System'
enhCat        : 'Graphics' | 'Gameplay' | 'Camera' | 'Quality of Life'
optSel        : int 0–4            // selected option row within the active tab+category
gfx           : { res:100, msaa:2, fps:60, matchHz:false,
                  tex:'Three-Point', texApplied:'Three-Point' }
enh           : { wide:'16:9', wideApplied:'16:9', hiRes:false,
                  aniso:4, sharp:25, fog:'Original' }
```

Externally supplied values (props in the prototype, game state in production): `heartsFull` 0–10 (default 8), `magicPct` 0–100 (default 62), `rupees` (default 218), `hoursRemaining` 0–72 (default 60 → `DAY 1/2/3` derived as `>48 ? 1 : >24 ? 2 : 3`), `refreshRate` 60/75/120/144 (default 60), `pauseEffect` Shimmer/Moonlight/Ember.

Derived values worth mirroring: `effFps = matchHz ? hz : min(fps, hz)`; slider fill `%` = `(v - lo) / (hi - lo) * 100`; `needsReload = tex !== texApplied || wide !== wideApplied`; the item description falls back to a per-tab generic line when an item has no bespoke copy.

No data fetching. In production, settings should persist to the emulator/game config; `texApplied` / `wideApplied` model "value written to config vs. value active in the running core".

---

## Assets

Included in this bundle:

- `uploads/pasted-1784906312069-0.png` — top-screen gameplay render (stand-in).
- `uploads/pasted-1784906025303-0.png` — Termina Field map render (stand-in, used by the gameplay bottom screen and the Map subscreen).
- `assets/dpad.png` — D-pad plate art.
- `assets/icons/*.png` — the eight icons used by the top-screen HUD (bomb, bow, bottle, hookshot, lens, maskFierce, ocarina, swordRazor).

Referenced but **not** bundled (they live in the project's `assets/mm/` folder — 48 PNGs, download the full project if you need them): the inventory icon set, named `assets/mm/<key>.png` where `<key>` is the identifier in the item/mask lists (e.g. `bomb`, `bow`, `arrowFire`, `maskFierce`, `bottleEmpty`). Any cell whose key is empty renders as an unrecovered slot.

Fonts are loaded from Google Fonts: Cinzel, Barlow, Chivo Mono.

**Note on IP:** the item, mask and area names in this prototype are placeholders standing in for the real game's content, and the icon/map art is temporary reference art. Replace both with the project's own assets and naming before shipping.

## Files

- `Pause Screen.dc.html` — the complete prototype: both screens, gameplay + paused states, all four subscreens, Options with both tabs and all Graphics rows. This is the reference implementation.
- `support.js` — the tiny runtime the prototype needs; open `Pause Screen.dc.html` directly in a browser with this file beside it.
- `assets/`, `uploads/` — the art listed above.
- Related explorations kept in the project (not required for implementation): `Second Screen Directions.dc.html` (direction studies for the second screen), `Pause Bottom Layouts.dc.html`, `Termina AMOLED.dc.html`, `Gameplay Screen.dc.html` (earlier gold/wood HUD direction — superseded by the purple/gold classic vocabulary documented here).

### Keyboard map (prototype only, for reviewing states quickly)

`P` / `+` toggle pause · `↑ ↓` move · `◄ ►` adjust · `Enter` select · `Esc` back.
