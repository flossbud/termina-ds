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
