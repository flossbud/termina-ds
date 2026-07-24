# Termina DS Phase 3: Bottom-Screen Gameplay HUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Phase 2 debug readout with the design handoff's §4 gameplay view — vitals bar, Termina map region with area label, and nav — plus idle/diagnostic plates, all fed by the existing read-only bridge.

**Architecture:** Pure Compose against a design-token module. A 10 Hz poll (unchanged from Phase 2) produces a `BridgeState`; a pure `route()` maps it to one of three screens; a pure `HudModel` derivation turns the snapshot into primitives so the HUD subtree only recomposes when a displayed value changes. No native code is touched.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.10.01), JUnit 4, bundled variable TTFs (Cinzel, Chivo Mono), Docker toolchain image `termina-ds-build:latest`.

**Spec:** `docs/superpowers/specs/2026-07-24-termina-ds-phase-3-gameplay-hud-design.md`
**Design source:** `docs/design/second-screen-handoff/README.md` (§4 is the screen being built)

## Global Constraints

- **No native changes.** No edits under `mm/`, no schema bump (`TDS_SNAP_SCHEMA_VERSION` stays 1), `lib2ship.so` untouched.
- **Do not modify** `GameSnapshotPoller.kt`, `GameSnapshot.kt`, `NativeBridge.kt`, or `SecondScreenPresentation.kt`.
- All Gradle work runs inside Docker via the repo's scripts. JVM tests: `./tools/run-unit-tests.sh` (self-verifying — trust its XML-derived counts, never raw Gradle console text).
- Full builds (`./tools/build-apk.sh`) take 8–19+ minutes and are only needed for install candidates. Run them in the background; a slow build is not a hung build.
- Both Thor displays are FLAG_SECURE: no screenshots. On-device visual checks are the user's, via the numbered hardware checklist in the spec (§10).
- Commits are authored as `jaret <jaretmsanchez@gmail.com>` (repo-local git config already set). Never involve the WheelHouse-Software GitHub account. Do not push.
- No `contentDescription` anywhere may embed `frameCounter` or any other value that changes every poll (spec §7).
- Design geometry is authored in design px against the 1240×1080 reference frame and flows through the `du`/`dus` helpers — never hardcode `.dp` for design dimensions.

---

### Task 1: Design tokens, fonts, and the scale frame

**Files:**
- Create: `Android/app/src/main/res/font/cinzel_variable.ttf` (downloaded)
- Create: `Android/app/src/main/res/font/chivo_mono_variable.ttf` (downloaded)
- Create: `docs/licenses/OFL-Cinzel.txt`, `docs/licenses/OFL-ChivoMono.txt` (downloaded)
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/DesignFrame.kt`
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/TerminaDesign.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/DesignFrameTest.kt`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces (later tasks rely on these exact names):
  - `DESIGN_WIDTH_PX`, `DESIGN_HEIGHT_PX`, `designScale(panelWidthPx: Float, panelHeightPx: Float): Float`
  - `object TerminaColors` (member names in the code below)
  - `object TerminaFonts { val Cinzel: FontFamily; val ChivoMono: FontFamily }`
  - `data class DesignTextSpec(family, weight, sizePx, trackingPx)` + `object TerminaType` (member names below)
  - `@Composable DesignTextSpec.toStyle(color: Color, shadow: Shadow? = null): TextStyle`
  - `val LocalDesignScale`, `@Composable DesignRoot(content)`, `@Composable du(designPx: Float): Dp`, `@Composable dus(designPx: Float): TextUnit`, `@Composable dupx(designPx: Float): Float`
  - `@Composable BreathingDiamond(modifier, sizePx = 9f, color = TerminaColors.Accent)`

- [ ] **Step 1: Download the fonts and licenses**

```bash
mkdir -p Android/app/src/main/res/font docs/licenses
curl -fsSL -o Android/app/src/main/res/font/cinzel_variable.ttf \
  "https://github.com/google/fonts/raw/main/ofl/cinzel/Cinzel%5Bwght%5D.ttf"
curl -fsSL -o Android/app/src/main/res/font/chivo_mono_variable.ttf \
  "https://github.com/google/fonts/raw/main/ofl/chivomono/ChivoMono%5Bwght%5D.ttf"
curl -fsSL -o docs/licenses/OFL-Cinzel.txt \
  "https://github.com/google/fonts/raw/main/ofl/cinzel/OFL.txt"
curl -fsSL -o docs/licenses/OFL-ChivoMono.txt \
  "https://github.com/google/fonts/raw/main/ofl/chivomono/OFL.txt"
file Android/app/src/main/res/font/*.ttf
```

Expected: both `.ttf` files report `TrueType Font data` and are several hundred KB. (Resource names are lowercase-with-underscores because Android resource names cannot contain brackets or capitals.)

- [ ] **Step 2: Write the failing test**

`Android/app/src/test/java/com/terminads/mm/secondscreen/DesignFrameTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignFrameTest {

    @Test
    fun nativePanelScalesToOne() {
        assertEquals(1f, designScale(1240f, 1080f), 1e-6f)
    }

    @Test
    fun uniformScaleUsesTheTighterAxis() {
        // Half-width panel: width is the constraint even though height fits.
        assertEquals(0.5f, designScale(620f, 1080f), 1e-6f)
    }

    @Test
    fun tallerPanelScalesByWidth() {
        assertEquals(1f, designScale(1240f, 2000f), 1e-6f)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./tools/run-unit-tests.sh --tests '*DesignFrameTest*'`
Expected: FAIL — compilation error, `Unresolved reference: designScale`.

- [ ] **Step 4: Write `DesignFrame.kt` (pure, no Android imports — this is what keeps it JVM-testable)**

```kotlin
package com.terminads.mm.secondscreen

/**
 * The design handoff's reference frame (docs/design/second-screen-handoff):
 * every §4 dimension is authored in these units. The Thor's bottom panel is
 * natively 1240x1080 so the scale is ~1.0 there, but nothing assumes it.
 */
const val DESIGN_WIDTH_PX = 1240f
const val DESIGN_HEIGHT_PX = 1080f

/**
 * One uniform scale factor from the actual panel to the design frame, using
 * the tighter axis so nothing stretches. Pure so the JVM tests can hold it.
 */
fun designScale(panelWidthPx: Float, panelHeightPx: Float): Float =
    minOf(panelWidthPx / DESIGN_WIDTH_PX, panelHeightPx / DESIGN_HEIGHT_PX)
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./tools/run-unit-tests.sh --tests '*DesignFrameTest*'`
Expected: PASS — 3 tests, 0 failures (counts from the script's XML summary).

- [ ] **Step 6: Write `TerminaDesign.kt`**

Every color is a token from the handoff README's "Design tokens" table; the type specs carry the exact size/weight/tracking pairs §4 uses.

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.Box
import com.terminads.mm.R

/** Design-token colors, named from the handoff README's token table. */
object TerminaColors {
    val ScreenBackground = Color(0xFF000000)
    val Ink = Color(0xFFEFE6FF)            // active tab labels, primary values
    val Ink2 = Color(0xFFE2D6F6)           // row labels, clock
    val Ink3 = Color(0xFFD7C6F4)           // subscreen titles / idle wordmark
    val InkMuted = Color(0xFFA58ED0)       // DAY label, chevrons
    val Accent = Color(0xFFB48CE8)         // diamonds, rules, glows
    val AccentLight = Color(0xFFCBB0F2)    // countdown chip text
    val AccentBright = Color(0xFFC9A2FF)   // active tab underline
    val Gold = Color(0xFFE0BD66)           // numeric values, double-defense rim
    val GoldLight = Color(0xFFF0D488)
    val GoldDim = Color(0xFFC9B17A)        // reload/stall bar text
    val TextDim = Color(0xFF6A5F85)
    val TextDimmer = Color(0xFF544D69)     // inactive tabs
    val TextDimmest = Color(0xFF3F3950)    // footer hints
    val TextHint = Color(0xFF4F4763)       // idle caption
    val ClockDim = Color(0xFF6F6288)       // AM/PM suffix
    val HeartRed = Color(0xFFFF4D5E)
    val HeartEmptyFill = Color(0xFF17161C)
    val HeartEmptyStroke = Color(0xFF3B3846)
    val MagicGreen = Color(0xFF4ADE80)
    val MagicTrack = Color(0xFF1E1C24)
    val RupeeGreen = Color(0xFF5EC46F)
    val VitalsInk = Color(0xFFEAEAEA)      // rupee count
    val AreaInk = Color(0xFFF0EEF5)        // area label, clock digits
    val ChipBorder = Color(0x73CBB0F2)     // rgba(203,176,242,.45)
    val WarningAmberBorder = Color(0x57E0BD66) // rgba(224,189,102,.34)
}

/**
 * Variable TTFs with explicit weight instances. If either font renders at a
 * uniform light weight on device, the variation settings path failed on that
 * OEM build -- escalate rather than ship (fallback would be static-instanced
 * TTFs, a deliberate decision, not a silent one).
 */
@OptIn(ExperimentalTextApi::class)
object TerminaFonts {
    val Cinzel = FontFamily(
        Font(
            R.font.cinzel_variable, weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
        Font(
            R.font.cinzel_variable, weight = FontWeight.ExtraBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(800)),
        ),
    )
    val ChivoMono = FontFamily(
        Font(
            R.font.chivo_mono_variable, weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            R.font.chivo_mono_variable, weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )
}

/** A text role: sizes and tracking in design px, resolved via dus() at use. */
@Immutable
data class DesignTextSpec(
    val family: FontFamily,
    val weight: FontWeight,
    val sizePx: Float,
    val trackingPx: Float = 0f,
)

/** §4 type roles plus the three delta screens (idle, diagnostic, stall chip). */
object TerminaType {
    val AreaLabel = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 20f, 3f)
    val NavTab = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 23f, 7f)
    val DayLabel = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 13f, 4f)
    val Clock = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 18f)
    val ClockSuffix = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 11f)
    val RupeeCount = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 17f)
    val CountdownChip = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 13f)
    val StallChip = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Medium, 13f, 3f)
    val IdleWordmark = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 48f)
    val IdleCaption = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Medium, 13f, 3f)
    val Diagnostic = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Medium, 16f, 1f)
}

/** Set by DesignRoot; 1f default keeps previews and tests harmless. */
val LocalDesignScale = compositionLocalOf { 1f }

/** design px -> Dp at the current scale. */
@Composable
fun du(designPx: Float): Dp =
    with(LocalDensity.current) { (designPx * LocalDesignScale.current).toDp() }

/** design px -> TextUnit. toSp() divides by fontScale, so glyphs stay px-true. */
@Composable
fun dus(designPx: Float): TextUnit =
    with(LocalDensity.current) { (designPx * LocalDesignScale.current).toSp() }

/** design px -> raw px at the current scale (shadow offsets, blur radii). */
@Composable
fun dupx(designPx: Float): Float = designPx * LocalDesignScale.current

@Composable
fun DesignTextSpec.toStyle(color: Color, shadow: Shadow? = null): TextStyle = TextStyle(
    color = color,
    fontFamily = family,
    fontWeight = weight,
    fontSize = dus(sizePx),
    letterSpacing = if (trackingPx != 0f) dus(trackingPx) else TextUnit.Unspecified,
    shadow = shadow,
)

/**
 * Root for every second-screen state: black background, one uniform design
 * scale published for du()/dus()/dupx().
 */
@Composable
fun DesignRoot(content: @Composable () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(TerminaColors.ScreenBackground)
    ) {
        val scale = designScale(
            constraints.maxWidth.toFloat(),
            constraints.maxHeight.toFloat(),
        )
        CompositionLocalProvider(LocalDesignScale provides scale) { content() }
    }
}

/**
 * The handoff's pzBreathe: opacity .4 -> 1 -> .4 over 2.1 s, applied to every
 * selection diamond. 9 px rotated square, 2 px corner radius, per the token
 * table's diamond rule.
 */
@Composable
fun BreathingDiamond(
    modifier: Modifier = Modifier,
    sizePx: Float = 9f,
    color: Color = TerminaColors.Accent,
) {
    val transition = rememberInfiniteTransition(label = "pzBreathe")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1050, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pzBreatheAlpha",
    )
    Box(
        modifier
            .size(du(sizePx))
            .rotate(45f)
            .alpha(alpha)
            .background(color, RoundedCornerShape(du(2f)))
    )
}
```

- [ ] **Step 7: Run the full suite to prove the module compiles**

Run: `./tools/run-unit-tests.sh`
Expected: PASS — 42 tests (39 existing + 3 new), 0 failures.

- [ ] **Step 8: Commit**

```bash
git add Android/app/src/main/res/font docs/licenses \
    Android/app/src/main/java/com/terminads/mm/secondscreen/DesignFrame.kt \
    Android/app/src/main/java/com/terminads/mm/secondscreen/TerminaDesign.kt \
    Android/app/src/test/java/com/terminads/mm/secondscreen/DesignFrameTest.kt
git commit -m "feat(secondscreen): add the Termina design tokens, fonts, and scale frame

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: The generated scene-name table

**Files:**
- Create: `tools/generate-scene-names.py`
- Create (generated): `Android/app/src/main/java/com/terminads/mm/secondscreen/SceneNames.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/SceneNamesTest.kt`

**Interfaces:**
- Consumes: nothing from other tasks (reads `mm/include/tables/scene_table.h` at generation time only — the app never parses C).
- Produces: `object SceneNames { fun forId(sceneId: Int): String?; val size: Int }`

- [ ] **Step 1: Write the failing test**

`Android/app/src/test/java/com/terminads/mm/secondscreen/SceneNamesTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneNamesTest {

    @Test
    fun knownScenesResolveToTheirCuratedNames() {
        // Spot checks against mm/include/tables/scene_table.h ordinals.
        assertEquals("Southern Swamp (Clear)", SceneNames.forId(0x00))
        assertEquals("Termina Field", SceneNames.forId(0x2D))
        assertEquals("South Clock Town", SceneNames.forId(0x6F))
    }

    @Test
    fun unsetGapsAreNullNotGarbage() {
        // Ids 1-6 are DEFINE_SCENE_UNSET in the table.
        for (id in 1..6) assertNull(SceneNames.forId(id))
    }

    @Test
    fun outOfRangeIdsAreNull() {
        assertNull(SceneNames.forId(-1))
        assertNull(SceneNames.forId(999))
    }

    @Test
    fun tableCarriesEveryNamedScene() {
        // 113 ordinals in scene_table.h, 102 of them DEFINE_SCENE with a name.
        assertEquals(102, SceneNames.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./tools/run-unit-tests.sh --tests '*SceneNamesTest*'`
Expected: FAIL — compilation error, `Unresolved reference: SceneNames`.

- [ ] **Step 3: Write the generator**

`tools/generate-scene-names.py`:

```python
#!/usr/bin/env python3
"""Regenerate SceneNames.kt from the scene table's humanName column.

The scene table (mm/include/tables/scene_table.h) is an X-macro list where the
ordinal position of each DEFINE_SCENE / DEFINE_SCENE_UNSET entry IS the sceneId
(the /* 0xNN */ comments confirm it). 2S2H added a curated humanName as the
last quoted argument of DEFINE_SCENE; DEFINE_SCENE_UNSET entries occupy an id
but have no name.

Parsing note: DEFINE_SCENE arguments contain nested parentheses
(PERSISTENT_CYCLE_FLAGS_SET(...)), so this matches whole lines and takes the
LAST quoted string, rather than trying to balance parens.

Run from anywhere: python3 tools/generate-scene-names.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "mm/include/tables/scene_table.h"
OUT = ROOT / "Android/app/src/main/java/com/terminads/mm/secondscreen/SceneNames.kt"

names: dict[int, str] = {}
idx = 0
for line in SRC.read_text().splitlines():
    if "DEFINE_SCENE_UNSET(" in line:
        idx += 1
    elif re.search(r"^\s*/\* 0x[0-9A-Fa-f]+ \*/ DEFINE_SCENE\(", line):
        quoted = re.findall(r'"([^"]*)"', line)
        if not quoted:
            sys.exit(f"scene id {idx}: DEFINE_SCENE line has no humanName string")
        names[idx] = quoted[-1]
        idx += 1

if idx == 0:
    sys.exit(f"parsed zero scene entries from {SRC} -- table format changed?")


def kotlin_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")


rows = "\n".join(
    f'        {sid} to "{kotlin_escape(name)}",' for sid, name in sorted(names.items())
)
OUT.write_text(f"""package com.terminads.mm.secondscreen

/**
 * GENERATED FILE -- do not edit by hand.
 *
 * Source: mm/include/tables/scene_table.h, humanName column (2S2H's curated
 * names, the same ones Better Map Select shows). Ordinal position in that
 * X-macro table is the sceneId. Regenerate after any scene-table change with:
 *
 *   python3 tools/generate-scene-names.py
 *
 * Unset ordinals (DEFINE_SCENE_UNSET) are absent: forId returns null and the
 * HUD falls back to "SCENE <id>" rather than guessing.
 */
object SceneNames {{
    fun forId(sceneId: Int): String? = TABLE[sceneId]

    val size: Int get() = TABLE.size

    private val TABLE: Map<Int, String> = mapOf(
{rows}
    )
}}
""")
print(f"wrote {OUT} ({len(names)} named scenes of {idx} ordinals)")
```

- [ ] **Step 4: Run the generator**

```bash
chmod +x tools/generate-scene-names.py
python3 tools/generate-scene-names.py
```

Expected output: `wrote .../SceneNames.kt (102 named scenes of 113 ordinals)`
Spot-check the output file contains `45 to "Termina Field",` (0x2D = 45).

- [ ] **Step 5: Run the test to verify it passes**

Run: `./tools/run-unit-tests.sh --tests '*SceneNamesTest*'`
Expected: PASS — 4 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add tools/generate-scene-names.py \
    Android/app/src/main/java/com/terminads/mm/secondscreen/SceneNames.kt \
    Android/app/src/test/java/com/terminads/mm/secondscreen/SceneNamesTest.kt
git commit -m "feat(secondscreen): generate the scene-name table from the scene table

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: HudModel derivation and bridge-state routing

**Files:**
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/HudModel.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/HudModelTest.kt`
- Test: `Android/app/src/test/java/com/terminads/mm/secondscreen/RouteTest.kt`

**Interfaces:**
- Consumes: `SceneNames.forId(Int): String?` (Task 2); `BridgeState`, `GameSnapshot` (existing Phase 2 code, do not modify).
- Produces:
  - `data class HudModel(fullHearts: Int, partialSixteenths: Int, totalHearts: Int, doubleDefense: Boolean, magicPct: Int?, rupees: Int, dayLabel: String?, clockTime: String, clockSuffix: String, hoursChip: String?, areaName: String)`
  - `fun deriveHudModel(s: GameSnapshot): HudModel`
  - `fun vitalsDescription(m: HudModel): String`
  - `sealed interface ScreenKind` with `Gameplay(model: HudModel, stalledSeconds: Long?)`, `Idle(waitingForGame: Boolean)`, `Diagnostic(message: String)`
  - `fun route(state: BridgeState): ScreenKind`
  - Internal (tested): `minutesOfDay(timeOfDay: Int): Int`, `clockText(timeOfDay: Int): Pair<String, String>`, `remainingMinutes(day: Int, timeOfDay: Int): Int`

- [ ] **Step 1: Write the failing tests**

`Android/app/src/test/java/com/terminads/mm/secondscreen/HudModelTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import com.terminads.mm.GameSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HudModelTest {

    // Mirrors the engine's CLOCK_TIME(hr, min) with ceiling division so the
    // floor conversion back to minutes lands on the intended minute exactly.
    private fun clockTimeU16(h: Int, m: Int): Int = ((h * 60 + m) * 0x10000 + 1439) / 1440

    private fun snapshot(
        health: Int = 48,
        healthCapacity: Int = 48,
        magic: Int = 0,
        magicCapacity: Int = 0,
        rupees: Int = 0,
        day: Int = 1,
        timeOfDay: Int = 0x4000, // 6:00 AM
        doubleDefense: Boolean = false,
        hasPlayState: Boolean = true,
        sceneId: Int = 0x2D, // Termina Field
    ) = GameSnapshot(
        frameCounter = 1, health = health, healthCapacity = healthCapacity,
        magic = magic, magicCapacity = magicCapacity, magicLevel = 0,
        rupees = rupees, playerForm = 4, equippedMask = 0, day = day,
        timeOfDay = timeOfDay, isNight = false, doubleDefense = doubleDefense,
        buttonItems = listOf(255, 255, 255, 255), buttonAmmo = listOf(0, 0, 0, 0),
        hasPlayState = hasPlayState, hasPlayer = hasPlayState, sceneId = sceneId,
        roomNum = 0, playerX = 0f, playerY = 0f, playerZ = 0f, playerYaw = 0,
    )

    // ---- clock ----

    @Test
    fun clockConvertsTheEngineDayFraction() {
        assertEquals("12:00" to "AM", clockText(0x0000))
        assertEquals("6:00" to "AM", clockText(0x4000))
        assertEquals("12:00" to "PM", clockText(0x8000))
        assertEquals("7:40" to "AM", clockText(clockTimeU16(7, 40)))
        assertEquals("11:59" to "PM", clockText(0xFFFF))
    }

    // ---- countdown (spec §5: mirrors TIME_UNTIL_MOON_CRASH, z64save.h:564;
    //      a day runs 6 AM -> 6 AM, day does NOT increment at midnight) ----

    @Test
    fun countdownAtCycleStartIsSeventyTwoHours() {
        assertEquals(72 * 60, remainingMinutes(1, 0x4000))
    }

    @Test
    fun countdownAtDayOneMorningIsSeventyHours() {
        assertEquals(4220, remainingMinutes(1, clockTimeU16(7, 40)))
    }

    @Test
    fun countdownPastMidnightStaysOnDayOne() {
        // 2:00 AM with day still 1: 20 h elapsed, 52 h left.
        assertEquals(52 * 60, remainingMinutes(1, clockTimeU16(2, 0)))
    }

    @Test
    fun dawnOfTheFinalDayIsTwentyFourHours() {
        assertEquals(24 * 60, remainingMinutes(3, 0x4000))
    }

    @Test
    fun finalMinuteFloorsToZeroHours() {
        assertEquals(1, remainingMinutes(3, clockTimeU16(5, 59)))
        val m = deriveHudModel(snapshot(day = 3, timeOfDay = clockTimeU16(5, 59)))
        assertEquals("0 H", m.hoursChip)
    }

    @Test
    fun countdownClampsToZeroPastTheDeadline() {
        assertEquals(0, remainingMinutes(4, 0x4000))
    }

    @Test
    fun preCycleStateHidesDayAndCountdown() {
        val m = deriveHudModel(snapshot(day = 0))
        assertNull(m.dayLabel)
        assertNull(m.hoursChip)
    }

    // ---- hearts ----

    @Test
    fun heartsSplitIntoFullPartialAndTotal() {
        val m = deriveHudModel(snapshot(health = 41, healthCapacity = 48))
        assertEquals(2, m.fullHearts)
        assertEquals(9, m.partialSixteenths)
        assertEquals(3, m.totalHearts)
    }

    @Test
    fun fullHealthHasNoPartialHeart() {
        val m = deriveHudModel(snapshot(health = 48, healthCapacity = 48))
        assertEquals(3, m.fullHearts)
        assertEquals(0, m.partialSixteenths)
    }

    @Test
    fun twentyHeartsSurvive() {
        val m = deriveHudModel(snapshot(health = 320, healthCapacity = 320))
        assertEquals(20, m.totalHearts)
        assertEquals(20, m.fullHearts)
    }

    @Test
    fun healthBeyondCapacityClamps() {
        val m = deriveHudModel(snapshot(health = 64, healthCapacity = 48))
        assertEquals(3, m.fullHearts)
        assertEquals(0, m.partialSixteenths)
    }

    @Test
    fun zeroCapacityMeansZeroHearts() {
        val m = deriveHudModel(snapshot(health = 0, healthCapacity = 0))
        assertEquals(0, m.totalHearts)
    }

    // ---- magic ----

    @Test
    fun magicRailHiddenUntilAcquired() {
        assertNull(deriveHudModel(snapshot(magicCapacity = 0)).magicPct)
    }

    @Test
    fun magicPercentageMatchesTheDesignDefault() {
        // 30/48 -> 62%, the handoff's own default magicPct.
        assertEquals(62, deriveHudModel(snapshot(magic = 30, magicCapacity = 48)).magicPct)
    }

    @Test
    fun magicClampsIntoRange() {
        assertEquals(100, deriveHudModel(snapshot(magic = 99, magicCapacity = 48)).magicPct)
        assertEquals(0, deriveHudModel(snapshot(magic = -5, magicCapacity = 48)).magicPct)
    }

    // ---- area ----

    @Test
    fun areaNameIsUppercasedCuratedName() {
        assertEquals("TERMINA FIELD", deriveHudModel(snapshot(sceneId = 0x2D)).areaName)
    }

    @Test
    fun unknownSceneFallsBackHonestly() {
        assertEquals("SCENE 999", deriveHudModel(snapshot(sceneId = 999)).areaName)
    }

    // ---- accessibility ----

    @Test
    fun vitalsDescriptionReadsAsProse() {
        val m = deriveHudModel(
            snapshot(
                health = 128, healthCapacity = 160, magic = 30, magicCapacity = 48,
                rupees = 218, day = 1, timeOfDay = clockTimeU16(7, 40),
            )
        )
        assertEquals(
            "8 of 10 hearts. Magic 62 percent. 218 rupees. DAY 1, 7:40 AM, 70 hours left.",
            vitalsDescription(m),
        )
    }

    @Test
    fun vitalsDescriptionOmitsWhatIsHidden() {
        val m = deriveHudModel(snapshot(day = 0, magicCapacity = 0, rupees = 0))
        assertEquals("3 of 3 hearts. 0 rupees. 6:00 AM.", vitalsDescription(m))
    }

    // ---- the spec §7/§10 structural guard ----

    @Test
    fun hudModelCarriesNoPerPollNoise() {
        val fields = HudModel::class.java.declaredFields.map { it.name }.toSet()
        val expected = setOf(
            "fullHearts", "partialSixteenths", "totalHearts", "doubleDefense",
            "magicPct", "rupees", "dayLabel", "clockTime", "clockSuffix",
            "hoursChip", "areaName",
        )
        assertEquals(
            "HudModel changed. If you added a field, prove it is not per-poll " +
                "noise (frameCounter and friends re-announce under TalkBack and " +
                "defeat recomposition skipping), then update this list.",
            expected, fields,
        )
    }
}
```

`Android/app/src/test/java/com/terminads/mm/secondscreen/RouteTest.kt`:

```kotlin
package com.terminads.mm.secondscreen

import com.terminads.mm.BridgeState
import com.terminads.mm.GameSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    private fun snapshot(hasPlayState: Boolean) = GameSnapshot(
        frameCounter = 7, health = 48, healthCapacity = 48, magic = 0,
        magicCapacity = 0, magicLevel = 0, rupees = 0, playerForm = 4,
        equippedMask = 0, day = 1, timeOfDay = 0x4000, isNight = false,
        doubleDefense = false, buttonItems = listOf(255, 255, 255, 255),
        buttonAmmo = listOf(0, 0, 0, 0), hasPlayState = hasPlayState,
        hasPlayer = hasPlayState, sceneId = 0x2D, roomNum = 0,
        playerX = 0f, playerY = 0f, playerZ = 0f, playerYaw = 0,
    )

    @Test
    fun liveWithWorldShowsGameplay() {
        val screen = route(BridgeState.Live(snapshot(hasPlayState = true)))
        assertTrue(screen is ScreenKind.Gameplay)
        assertEquals(null, (screen as ScreenKind.Gameplay).stalledSeconds)
    }

    @Test
    fun liveWithoutWorldIdles() {
        // Title screen / file select: save slots are meaningless, never a HUD.
        assertEquals(ScreenKind.Idle(waitingForGame = false), route(BridgeState.Live(snapshot(false))))
    }

    @Test
    fun stalledWithWorldKeepsTheHudAndReportsSeconds() {
        val screen = route(BridgeState.Stalled(snapshot(true), millisSinceChange = 2400))
        assertEquals(2L, (screen as ScreenKind.Gameplay).stalledSeconds)
    }

    @Test
    fun stalledWithoutWorldIdles() {
        assertEquals(ScreenKind.Idle(waitingForGame = false), route(BridgeState.Stalled(snapshot(false), 2400)))
    }

    @Test
    fun noFramesYetIsTheWaitingIdle() {
        assertEquals(ScreenKind.Idle(waitingForGame = true), route(BridgeState.NoFramesYet))
    }

    @Test
    fun faultsKeepThePhase2DiagnosticStrings() {
        // docs/HANDOFF.md's diagnostic vocabulary must still match the screen.
        assertEquals(
            ScreenKind.Diagnostic("NATIVE NOT LOADED"),
            route(BridgeState.NativeUnavailable),
        )
        assertEquals(
            ScreenKind.Diagnostic("SCHEMA MISMATCH native=2 expected=1"),
            route(BridgeState.SchemaMismatch(nativeVersion = 2, expected = 1)),
        )
        assertEquals(
            ScreenKind.Diagnostic(
                "BUFFER TOO SMALL kotlin=27 slots < native payload " +
                    "(GameSnapshotLayout must mirror GameSnapshot.h)"
            ),
            route(BridgeState.BufferTooSmall(kotlinSlots = 27)),
        )
        assertEquals(
            ScreenKind.Diagnostic("UNKNOWN READ STATUS (native is newer than this build's Kotlin)"),
            route(BridgeState.UnknownReadStatus),
        )
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./tools/run-unit-tests.sh --tests '*HudModelTest*' --tests '*RouteTest*'`
Expected: FAIL — compilation errors, `Unresolved reference: deriveHudModel` / `route`.

- [ ] **Step 3: Write `HudModel.kt`**

```kotlin
package com.terminads.mm.secondscreen

import com.terminads.mm.BridgeState
import com.terminads.mm.GameSnapshot
import java.util.Locale

/**
 * Everything the gameplay HUD draws, and nothing else.
 *
 * Deliberately primitives-and-Strings so it is stable for Compose and
 * structurally comparable: the HUD subtree recomposes only when a displayed
 * value changed, not at the 10 Hz poll rate. frameCounter must never appear
 * here -- HudModelTest.hudModelCarriesNoPerPollNoise enforces the field list.
 */
data class HudModel(
    val fullHearts: Int,
    /** 1/16-heart fill of the heart after the full ones; 0 = none partial. */
    val partialSixteenths: Int,
    val totalHearts: Int,
    val doubleDefense: Boolean,
    /** null = magic not yet acquired: hide the rail, don't show it empty. */
    val magicPct: Int?,
    val rupees: Int,
    /** "DAY 1"; null hidden while day < 1 (pre-cycle intro state). */
    val dayLabel: String?,
    val clockTime: String,
    val clockSuffix: String,
    /** "70 H"; null hidden while day < 1. */
    val hoursChip: String?,
    val areaName: String,
)

/** Engine u16 day fraction (0x10000 = 24 h, 0 = midnight) -> minutes. */
internal fun minutesOfDay(timeOfDay: Int): Int = timeOfDay * 1440 / 0x10000

/** 12-hour clock text: "7:40" to "AM". */
internal fun clockText(timeOfDay: Int): Pair<String, String> {
    val minutes = minutesOfDay(timeOfDay)
    val h24 = minutes / 60
    val minute = minutes % 60
    val suffix = if (h24 < 12) "AM" else "PM"
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return String.format(Locale.ROOT, "%d:%02d", h12, minute) to suffix
}

/**
 * Minutes until the moon falls (Day 4, 6:00 AM). Mirrors the engine's
 * TIME_UNTIL_MOON_CRASH (z64save.h:564): the day slot does NOT increment at
 * midnight -- a day runs 6 AM to 6 AM -- so elapsed time within the current
 * day is wrapped relative to 6:00 AM, not to midnight.
 */
internal fun remainingMinutes(day: Int, timeOfDay: Int): Int {
    val elapsedInDay = Math.floorMod(minutesOfDay(timeOfDay) - 360, 1440)
    return ((4 - day) * 1440 - elapsedInDay).coerceAtLeast(0)
}

fun deriveHudModel(s: GameSnapshot): HudModel {
    val capacity = s.healthCapacity.coerceAtLeast(0)
    val health = s.health.coerceIn(0, capacity)
    val (time, suffix) = clockText(s.timeOfDay)
    val inCycle = s.day >= 1
    return HudModel(
        fullHearts = health / 16,
        partialSixteenths = health % 16,
        totalHearts = capacity / 16,
        doubleDefense = s.doubleDefense,
        magicPct = if (s.magicCapacity <= 0) {
            null
        } else {
            (s.magic * 100 / s.magicCapacity).coerceIn(0, 100)
        },
        rupees = s.rupees,
        dayLabel = if (inCycle) "DAY ${s.day}" else null,
        clockTime = time,
        clockSuffix = suffix,
        hoursChip = if (inCycle) "${remainingMinutes(s.day, s.timeOfDay) / 60} H" else null,
        areaName = SceneNames.forId(s.sceneId)?.uppercase(Locale.ROOT) ?: "SCENE ${s.sceneId}",
    )
}

/**
 * The vitals bar's single TalkBack node (spec §7): prose, composed once, no
 * per-poll values. The fastest-changing token is the clock minute.
 */
fun vitalsDescription(m: HudModel): String {
    val parts = mutableListOf<String>()
    val partial = if (m.partialSixteenths > 0) " and a partial heart" else ""
    parts += "${m.fullHearts} of ${m.totalHearts} hearts$partial"
    m.magicPct?.let { parts += "Magic $it percent" }
    parts += "${m.rupees} rupees"
    val clock = "${m.clockTime} ${m.clockSuffix}"
    parts += if (m.dayLabel != null && m.hoursChip != null) {
        "${m.dayLabel}, $clock, ${m.hoursChip.removeSuffix(" H")} hours left"
    } else {
        clock
    }
    return parts.joinToString(". ") + "."
}

/** Which screen the host shows. A pure function of the bridge state. */
sealed interface ScreenKind {
    data class Gameplay(val model: HudModel, val stalledSeconds: Long?) : ScreenKind
    data class Idle(val waitingForGame: Boolean) : ScreenKind
    data class Diagnostic(val message: String) : ScreenKind
}

/**
 * Routing per spec §4. The diagnostic strings are copied verbatim from the
 * Phase 2 debug readout so docs/HANDOFF.md's fault vocabulary still matches
 * what the screen shows; RouteTest pins them.
 */
fun route(state: BridgeState): ScreenKind = when (state) {
    is BridgeState.Live ->
        if (state.snapshot.hasPlayState) {
            ScreenKind.Gameplay(deriveHudModel(state.snapshot), stalledSeconds = null)
        } else {
            ScreenKind.Idle(waitingForGame = false)
        }
    is BridgeState.Stalled ->
        if (state.snapshot.hasPlayState) {
            ScreenKind.Gameplay(deriveHudModel(state.snapshot), state.millisSinceChange / 1000)
        } else {
            ScreenKind.Idle(waitingForGame = false)
        }
    BridgeState.NoFramesYet -> ScreenKind.Idle(waitingForGame = true)
    BridgeState.NativeUnavailable -> ScreenKind.Diagnostic("NATIVE NOT LOADED")
    is BridgeState.SchemaMismatch -> ScreenKind.Diagnostic(
        "SCHEMA MISMATCH native=${state.nativeVersion} expected=${state.expected}"
    )
    is BridgeState.BufferTooSmall -> ScreenKind.Diagnostic(
        "BUFFER TOO SMALL kotlin=${state.kotlinSlots} slots < native payload " +
            "(GameSnapshotLayout must mirror GameSnapshot.h)"
    )
    BridgeState.UnknownReadStatus -> ScreenKind.Diagnostic(
        "UNKNOWN READ STATUS (native is newer than this build's Kotlin)"
    )
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./tools/run-unit-tests.sh --tests '*HudModelTest*' --tests '*RouteTest*'`
Expected: PASS — 27 tests (21 HudModelTest + 6 RouteTest), 0 failures.

- [ ] **Step 5: Run the full suite**

Run: `./tools/run-unit-tests.sh`
Expected: PASS — 73 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/HudModel.kt \
    Android/app/src/test/java/com/terminads/mm/secondscreen/HudModelTest.kt \
    Android/app/src/test/java/com/terminads/mm/secondscreen/RouteTest.kt
git commit -m "feat(secondscreen): derive the HUD model and route bridge states

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: The gameplay screen

**Files:**
- Create: `Android/app/src/main/res/drawable-nodpi/termina_map.png` (copied)
- Create: `Android/app/src/main/java/com/terminads/mm/secondscreen/GameplayScreen.kt`

**Interfaces:**
- Consumes: everything Task 1 produces; `HudModel`, `vitalsDescription` (Task 3); `R.drawable.termina_map`.
- Produces: `@Composable GameplayScreen(model: HudModel, stalledSeconds: Long?)` — exactly the signature Task 5's host calls.

No new JVM tests: every formula this screen draws was tested in Task 3; this
task is layout. The full-suite run is the compile gate.

- [ ] **Step 1: Copy the map art**

```bash
mkdir -p Android/app/src/main/res/drawable-nodpi
cp docs/design/second-screen-handoff/uploads/pasted-1784906025303-0.png \
   Android/app/src/main/res/drawable-nodpi/termina_map.png
```

(`drawable-nodpi` so Android never density-scales it; `ContentScale.Crop` does
the fitting, per handoff §4 "full-bleed, no vignette".)

- [ ] **Step 2: Write `GameplayScreen.kt`**

Geometry comments cite handoff §4; every dimension is design px through `du`.

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.terminads.mm.R

/**
 * Handoff §4: the bottom-screen gameplay view. Vitals bar (64), map region
 * (fills), nav (104). ITEMS and MASKS are rendered but inert until Phase 5's
 * write bridge can pause the game.
 */
@Composable
fun GameplayScreen(model: HudModel, stalledSeconds: Long?) {
    Column(Modifier.fillMaxSize()) {
        VitalsBar(model, Modifier.fillMaxWidth().height(du(64f)))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Image(
                painter = painterResource(R.drawable.termina_map),
                contentDescription = "Map of Termina",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = model.areaName,
                style = TerminaType.AreaLabel.toStyle(
                    TerminaColors.AreaInk,
                    shadow = Shadow(
                        color = Color(0xE6000000), // rgba(0,0,0,.9)
                        offset = Offset(0f, dupx(2f)),
                        blurRadius = dupx(10f),
                    ),
                ),
                modifier = Modifier
                    .padding(start = du(30f), top = du(14f))
                    .semantics { contentDescription = "Area: ${model.areaName}" },
            )
            if (stalledSeconds != null) {
                StallChip(
                    stalledSeconds,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = du(30f), top = du(14f)),
                )
            }
        }
        NavBar(Modifier.fillMaxWidth().height(du(104f)))
    }
}

// ---- vitals bar ----

@Composable
private fun VitalsBar(model: HudModel, modifier: Modifier) {
    val description = vitalsDescription(model)
    Row(
        // One semantic node for TalkBack (spec §7): prose, not per-glyph noise.
        modifier = modifier
            .padding(horizontal = du(30f))
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeartRows(model)
        model.magicPct?.let { pct ->
            // 130x5 rail, green fill at pct%, both fully rounded.
            Box(
                Modifier
                    .padding(start = du(12f))
                    .width(du(130f))
                    .height(du(5f))
                    .background(TerminaColors.MagicTrack, RoundedCornerShape(50)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(pct / 100f)
                        .background(TerminaColors.MagicGreen, RoundedCornerShape(50)),
                )
            }
        }
        Row(
            Modifier.padding(start = du(14f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 11px rupee diamond: rotated square, 2px corner radius.
            Box(
                Modifier
                    .size(du(11f))
                    .rotate(45f)
                    .background(TerminaColors.RupeeGreen, RoundedCornerShape(du(2f))),
            )
            Text(
                "${model.rupees}",
                style = TerminaType.RupeeCount.toStyle(TerminaColors.VitalsInk),
                modifier = Modifier.padding(start = du(8f)),
            )
        }
        Spacer(Modifier.weight(1f))
        model.dayLabel?.let {
            Text(
                it,
                style = TerminaType.DayLabel.toStyle(TerminaColors.InkMuted),
                modifier = Modifier.padding(end = du(18f)),
            )
        }
        Row {
            Text(
                model.clockTime,
                style = TerminaType.Clock.toStyle(TerminaColors.AreaInk),
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                model.clockSuffix,
                style = TerminaType.ClockSuffix.toStyle(TerminaColors.ClockDim),
                modifier = Modifier.alignByBaseline().padding(start = du(4f)),
            )
        }
        model.hoursChip?.let { chip ->
            Box(
                Modifier
                    .padding(start = du(14f))
                    .border(du(1f), TerminaColors.ChipBorder, RoundedCornerShape(du(6f)))
                    .padding(horizontal = du(10f), vertical = du(3f)),
            ) {
                Text(chip, style = TerminaType.CountdownChip.toStyle(TerminaColors.AccentLight))
            }
        }
    }
}

@Composable
private fun HeartRows(model: HudModel) {
    // Fill fraction per heart index; the heart after the full ones carries the
    // partial sixteenths. Rows wrap at 10 like the original game's HUD.
    val fills = List(model.totalHearts) { i ->
        when {
            i < model.fullHearts -> 1f
            i == model.fullHearts -> model.partialSixteenths / 16f
            else -> 0f
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(du(3f))) {
        fills.chunked(10).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(du(3f))) {
                row.forEach { fill -> Heart(fill, model.doubleDefense) }
            }
        }
    }
}

@Composable
private fun Heart(fillFraction: Float, doubleDefense: Boolean) {
    val strokePx = dupx(1.4f)
    Canvas(Modifier.size(du(18f))) {
        val path = heartPath(size.width)
        drawPath(path, TerminaColors.HeartEmptyFill)
        if (fillFraction > 0f) {
            clipRect(right = size.width * fillFraction) {
                drawPath(path, TerminaColors.HeartRed)
            }
        }
        when {
            // Double defense: gold rim on every heart (delta noted in spec §6;
            // the handoff's token table has no double-defense treatment).
            doubleDefense -> drawPath(path, TerminaColors.Gold, style = Stroke(strokePx))
            // Empty/partial hearts keep the handoff's empty-heart stroke.
            fillFraction < 1f ->
                drawPath(path, TerminaColors.HeartEmptyStroke, style = Stroke(strokePx))
        }
    }
}

/** A filled heart silhouette in a size x size box, lobes up. */
private fun heartPath(size: Float): Path = Path().apply {
    moveTo(0.50f * size, 0.30f * size)
    cubicTo(0.50f * size, 0.12f * size, 0.30f * size, 0.02f * size, 0.16f * size, 0.12f * size)
    cubicTo(0.02f * size, 0.24f * size, 0.06f * size, 0.44f * size, 0.20f * size, 0.60f * size)
    cubicTo(0.32f * size, 0.74f * size, 0.50f * size, 0.90f * size, 0.50f * size, 0.90f * size)
    cubicTo(0.50f * size, 0.90f * size, 0.68f * size, 0.74f * size, 0.80f * size, 0.60f * size)
    cubicTo(0.94f * size, 0.44f * size, 0.98f * size, 0.24f * size, 0.84f * size, 0.12f * size)
    cubicTo(0.70f * size, 0.02f * size, 0.50f * size, 0.12f * size, 0.50f * size, 0.30f * size)
    close()
}

// ---- stall chip (spec §6 delta: the handoff has no stalled concept) ----

@Composable
private fun StallChip(seconds: Long, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(du(1f), TerminaColors.WarningAmberBorder, RoundedCornerShape(du(6f)))
            .padding(horizontal = du(10f), vertical = du(3f))
            // Static description: the visible text ticks once a second, and
            // per-poll/per-second values are banned from semantics (spec §7).
            .clearAndSetSemantics { contentDescription = "Bridge stalled" },
    ) {
        Text(
            "STALLED ${seconds}s",
            style = TerminaType.StallChip.toStyle(TerminaColors.GoldDim),
        )
    }
}

// ---- nav ----

@Composable
private fun NavBar(modifier: Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(du(56f), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab("MAP", active = true)
        NavTab("ITEMS", active = false)
        NavTab("MASKS", active = false)
    }
}

/**
 * Handoff §4 nav: 46 tall, 2px underline. Active = Ink + AccentBright
 * underline; inactive = TextDimmer + transparent, non-interactive until
 * Phase 5 can pause the game -- no click modifier at all, and TalkBack hears
 * them as disabled.
 */
@Composable
private fun NavTab(label: String, active: Boolean) {
    val ink = if (active) TerminaColors.Ink else TerminaColors.TextDimmer
    val underline = if (active) TerminaColors.AccentBright else Color.Transparent
    Column(
        Modifier
            .height(du(46f))
            .semantics {
                if (active) {
                    contentDescription = "$label tab, current view"
                } else {
                    contentDescription = "$label tab, unavailable"
                    disabled()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = TerminaType.NavTab.toStyle(ink))
        Box(
            Modifier
                .fillMaxWidth()
                .height(du(2f))
                .background(underline),
        )
    }
}
```

- [ ] **Step 3: Run the full suite as the compile gate**

Run: `./tools/run-unit-tests.sh`
Expected: PASS — 73 tests, 0 failures (test compilation compiles the main
source set, so this proves `GameplayScreen.kt` builds).

- [ ] **Step 4: Commit**

```bash
git add Android/app/src/main/res/drawable-nodpi/termina_map.png \
    Android/app/src/main/java/com/terminads/mm/secondscreen/GameplayScreen.kt
git commit -m "feat(secondscreen): build the gameplay HUD screen

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Host rewrite and the UI-only build path

**Files:**
- Modify: `Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt` (full rewrite below — the Phase 2 debug readout is deleted wholesale, per its own file comment)
- Create: `tools/assemble-apk.sh`

**Interfaces:**
- Consumes: `DesignRoot`, `BreathingDiamond`, `TerminaType`, `TerminaColors`, `du` (Task 1); `route`, `ScreenKind` (Task 3); `GameplayScreen(model, stalledSeconds)` (Task 4); `DisplayInfo` (existing, unmodified — fields `displayId`, `name`, `widthPx`, `heightPx`, `refreshRate`).
- Produces: the same public signature `SecondScreenHost(displayInfo, pollBridge, pollIntervalMillis)` that `SecondScreenPresentation.kt` already calls — the presentation file is NOT modified.

- [ ] **Step 1: Rewrite `SecondScreenHost.kt`**

```kotlin
package com.terminads.mm.secondscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.terminads.mm.BridgeState
import kotlinx.coroutines.delay

/**
 * The second screen: polls the bridge at 10 Hz on the main thread and routes
 * the result to the gameplay HUD, the idle plate, or the diagnostic plate.
 *
 * The Phase 2 debug readout that used to live here is deleted, as its own
 * charter required; git history keeps it, and the diagnostic plate preserves
 * its exact fault strings.
 */
@Composable
fun SecondScreenHost(
    displayInfo: DisplayInfo,
    pollBridge: () -> BridgeState,
    pollIntervalMillis: Long = 100L,
) {
    var state by remember { mutableStateOf<BridgeState>(BridgeState.NoFramesYet) }

    // Main-thread coroutine scoped to this composition: starts when the
    // Presentation shows, stops when it dismisses. The Presentation lifecycle
    // owner drops the main-thread assertion, so nothing here may leave the
    // main thread.
    LaunchedEffect(pollBridge, pollIntervalMillis) {
        while (true) {
            state = pollBridge()
            delay(pollIntervalMillis)
        }
    }

    DesignRoot {
        when (val screen = route(state)) {
            is ScreenKind.Gameplay -> GameplayScreen(screen.model, screen.stalledSeconds)
            is ScreenKind.Idle -> IdlePlate(screen.waitingForGame)
            is ScreenKind.Diagnostic -> DiagnosticPlate(screen.message, displayInfo)
        }
    }
}

/**
 * Spec §6 delta: the no-save state the handoff doesn't cover (title screen,
 * file select, game still booting). Quiet plate in the design vocabulary.
 */
@Composable
private fun IdlePlate(waitingForGame: Boolean) {
    val description = if (waitingForGame) "Termina DS, waiting for the game" else "Termina DS"
    Column(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "TERMINA DS",
            style = TerminaType.IdleWordmark
                .toStyle(TerminaColors.Ink3)
                .copy(letterSpacing = 0.18.em),
        )
        BreathingDiamond(Modifier.padding(top = du(22f)))
        if (waitingForGame) {
            Text(
                "WAITING FOR THE GAME",
                style = TerminaType.IdleCaption.toStyle(TerminaColors.TextHint),
                modifier = Modifier.padding(top = du(18f)),
            )
        }
    }
}

/**
 * Build-skew faults stay loud (spec §4): the exact Phase 2 strings, centered,
 * with the display identity beneath -- on a FLAG_SECURE screen this text is
 * the only witness.
 */
@Composable
private fun DiagnosticPlate(message: String, displayInfo: DisplayInfo) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(du(44f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = TerminaType.Diagnostic.toStyle(TerminaColors.GoldDim),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Bridge fault: $message" },
        )
        Text(
            "display ${displayInfo.displayId} ${displayInfo.name} " +
                "${displayInfo.widthPx}x${displayInfo.heightPx}",
            style = TerminaType.IdleCaption.toStyle(TerminaColors.TextDimmest),
            modifier = Modifier.padding(top = du(18f)),
        )
    }
}
```

- [ ] **Step 2: Run the full suite**

Run: `./tools/run-unit-tests.sh`
Expected: PASS — 73 tests, 0 failures. (The deleted debug readout had no
tests of its own; nothing should drop.)

- [ ] **Step 3: Write `tools/assemble-apk.sh`**

```bash
#!/usr/bin/env bash
# Assemble the release APK WITHOUT the full pipeline -- for Kotlin/resource
# iteration only.
#
# tools/build-apk.sh clears Android/app/.cxx on purpose so CMake's GLOB_RECURSE
# re-freezes the native source list; that is mandatory when .c/.cpp files were
# added or removed, and costs 8-19 minutes. This script skips the clear and the
# o2r stage entirely, so Gradle reuses the existing native build. NEVER use it
# after native source changes -- the stale glob would silently ship an old
# native lib (docs/HANDOFF.md, the GLOB_RECURSE trap).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${TERMINA_BUILD_IMAGE:-termina-ds-build:latest}"

docker run --rm \
    -v "${REPO_ROOT}:/workspace" \
    -v "termina-ds-gradle:/root/.gradle" \
    -v "termina-ds-android-home:/root/.android" \
    -e ANDROID_KEYSTORE_PATH="${ANDROID_KEYSTORE_PATH:-}" \
    -e ANDROID_KEYSTORE_PASSWORD="${ANDROID_KEYSTORE_PASSWORD:-}" \
    -e ANDROID_KEY_ALIAS="${ANDROID_KEY_ALIAS:-}" \
    -e ANDROID_KEY_PASSWORD="${ANDROID_KEY_PASSWORD:-}" \
    -w /workspace/Android \
    "${IMAGE}" ./gradlew --no-daemon :app:assembleRelease

echo "==> APK: ${REPO_ROOT}/Android/app/build/outputs/apk/release/app-release.apk"
```

```bash
chmod +x tools/assemble-apk.sh
```

- [ ] **Step 4: Prove the UI-only path assembles**

Run in the background (first run may still take several minutes while Gradle
warms up; it must NOT reconfigure CMake from scratch):

```bash
./tools/assemble-apk.sh
```

Expected: `BUILD SUCCESSFUL`, and the echoed APK path exists with a fresh
timestamp. If Gradle reports the externalNativeBuild task doing a full CMake
reconfigure and the build heads past ~20 minutes, stop and investigate — the
`.cxx` directory from the last full build is missing.

- [ ] **Step 5: Commit**

```bash
git add Android/app/src/main/java/com/terminads/mm/secondscreen/SecondScreenHost.kt \
    tools/assemble-apk.sh
git commit -m "feat(secondscreen): route the second screen through the designed HUD

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Install candidate and handoff docs

**Files:**
- Modify: `docs/HANDOFF.md` (Phase 3 section — content below)

- [ ] **Step 1: Full pipeline build, backgrounded**

```bash
./tools/build-apk.sh
```

Expected: BUILD SUCCESSFUL after 8–19+ minutes (it force-clears `.cxx`; slow
is normal, do not kill it). This is the install candidate — the full pipeline,
not the UI-only script, so the artifact is exactly what the standing process
would produce.

- [ ] **Step 2: Verify the packaged native symbols (in Docker — llvm-nm is NOT on the host)**

```bash
docker run --rm -v "$(pwd)":/workspace -w /workspace termina-ds-build:latest \
  bash -euo pipefail -c '
    apk=Android/app/build/outputs/apk/release/app-release.apk
    rm -rf /tmp/apk && unzip -q -o -d /tmp/apk "$apk" "lib/arm64-v8a/lib2ship.so"
    "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm -D \
      /tmp/apk/lib/arm64-v8a/lib2ship.so \
      | grep -E "Java_com_terminads_mm_NativeBridge_(nativeReadSnapshot|nativeGetUptimeMillis)"
    unzip -l "$apk" | grep -q "mm\.o2r" && { echo "FAIL: mm.o2r leaked into APK"; exit 1; }
    echo "symbols + o2r check OK"'
```

Expected: both symbols listed with type `T`, then `symbols + o2r check OK`.
(Native was untouched, so this is a regression gate, not a feature check.)

- [ ] **Step 3: Install on the Thor**

```bash
adb devices
adb install -r Android/app/build/outputs/apk/release/app-release.apk
```

Expected: `Success`. If no device is listed, the Thor's Wi-Fi adb (10.0.0.30)
is down — ask the user to reconnect rather than guessing at ports.

- [ ] **Step 4: Update `docs/HANDOFF.md`**

In the phase status section, mark Phase 3 implemented (pending hardware
verification), and add to the file table:

```markdown
| `Android/.../secondscreen/DesignFrame.kt` | Pure design-frame scaling (1240x1080 reference) |
| `Android/.../secondscreen/TerminaDesign.kt` | Design tokens: colors, bundled fonts, type specs, DesignRoot |
| `Android/.../secondscreen/SceneNames.kt` | GENERATED sceneId -> name table (tools/generate-scene-names.py) |
| `Android/.../secondscreen/HudModel.kt` | Pure snapshot -> HUD model, route(), diagnostic strings |
| `Android/.../secondscreen/GameplayScreen.kt` | The §4 gameplay HUD (vitals, map, nav) |
| `tools/assemble-apk.sh` | UI-only APK assembly -- NEVER after native changes (stale glob) |
| `tools/generate-scene-names.py` | Regenerates SceneNames.kt from scene_table.h |
| `docs/design/second-screen-handoff/` | The committed design handoff (README.md is the visual source of truth) |
```

Also add one warning line wherever the GLOB_RECURSE trap is documented:
`tools/assemble-apk.sh` exists precisely because it skips the `.cxx` clear —
it is the fast path for UI work and a foot-gun after native work.

- [ ] **Step 5: Commit**

```bash
git add docs/HANDOFF.md
git commit -m "docs: record the Phase 3 build and the UI-only assembly path

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

- [ ] **Step 6: Hand to the user**

Present the spec §10 hardware checklist (items 1–11) as a numbered list for
the user to run on the Thor. The verification record
(`docs/verification/2026-07-24-phase-3-thor.md`) is written only after the
user reports results — it records what was seen, not what was hoped.

---

## Self-Review Notes

- **Spec coverage:** §3 files → Tasks 1–5; §4 routing → Task 3 (`route` +
  RouteTest); §5 formulas → Task 3 (all vectors from §10 present, including
  the corrected midnight-wrap countdown); §6 visuals → Tasks 4–5 (tokens cite
  the handoff sections inline); §7 accessibility → `clearAndSetSemantics` on
  the vitals bar and stall chip, disabled nav tabs, the structural guard test;
  §8 assets → Tasks 1 (fonts/licenses) and 4 (map); §9 build → Task 5's
  `assemble-apk.sh`; §10 JVM tests → Tasks 1–3; §10 hardware → Task 6 step 6;
  §11 invariants → Global Constraints; §12 inheritance → the module boundaries
  themselves.
- **Type consistency:** `GameplayScreen(model: HudModel, stalledSeconds: Long?)`
  is identical in Task 4's definition and Task 5's call site;
  `ScreenKind.Gameplay(model, stalledSeconds)` field names match between
  Task 3's definition and Task 5's `when`; `DesignTextSpec.toStyle(color,
  shadow)` matches every use in Tasks 4–5; `SceneNames.forId`/`size` match
  Task 2's generated object and Task 3's caller/tests.
- **Test-count arithmetic:** 39 existing + 3 (Task 1) = 42; + 4 (Task 2) = 46;
  + 27 (Task 3) = 73. The full-suite expectations in Tasks 3–5 use these
  numbers; if an existing test was meanwhile added or removed, trust the
  script's failure/error counts, not the exact total.
