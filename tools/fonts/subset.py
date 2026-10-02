#!/usr/bin/env python3
"""Subsets Material Symbols Rounded to the glyphs Culvery names in tools/fonts/icons.txt (4c design §3.5).

Keeps all four axes (FILL, GRAD, opsz, wght), the ligatures and the filled alternates, and writes the font the app bundles:
core/ui/src/main/res/font/material_symbols_rounded.ttf. Needs fontTools (python -m pip install --user fonttools).

Usage: python tools/fonts/subset.py --source <the full Material Symbols Rounded variable .ttf>
"""
import argparse
import pathlib
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
ICONS = ROOT / "tools" / "fonts" / "icons.txt"
OUT = ROOT / "core" / "ui" / "src" / "main" / "res" / "font" / "material_symbols_rounded.ttf"
SINGLE = 1
LIGATURE = 4
EXTENSION = 7


def substitutions(font):
    """Each ligature as (its component glyph names) -> the glyph it makes; and each single substitution, glyph -> glyph.

    The single substitutions include rclt's filled alternates, which the font's FeatureVariations turn on at FILL >= 0.99.
    """
    ligatures, singles = {}, {}
    for lookup in font["GSUB"].table.LookupList.Lookup:
        for table in lookup.SubTable:
            if lookup.LookupType == EXTENSION:
                table = table.ExtSubTable
            if table.LookupType == SINGLE:
                singles.update(table.mapping)
            elif table.LookupType == LIGATURE:
                for first, ligs in table.ligatures.items():
                    for lig in ligs:
                        ligatures[tuple([first] + list(lig.Component))] = lig.LigGlyph
    return ligatures, singles


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--source", required=True, help="the full Material Symbols Rounded variable font")
    args = parser.parse_args()

    names = [line.strip() for line in ICONS.read_text(encoding="utf-8").splitlines()]
    names = [name for name in names if name and not name.startswith("#")]
    font = TTFont(args.source)
    cmap = font.getBestCmap()
    found, singles = substitutions(font)

    glyphs, missing = set(), []
    for name in names:
        sequence = tuple(cmap.get(ord(c)) for c in name)
        if None in sequence or sequence not in found:
            missing.append(name)
            continue
        glyphs.update(sequence)
        glyphs.add(found[sequence])
    if missing:
        sys.exit("No ligature in the source font for: " + ", ".join(missing))
    # HhIcon(filled = true) draws each glyph's filled alternate: keep those too.
    glyphs.update(singles[g] for g in list(glyphs) if g in singles)

    options = subset.Options()
    # Without this the subsetter follows every ligature from the kept letters and keeps almost the whole font.
    options.layout_closure = False
    # Every feature, rclt included, and with them the FeatureVariations that switch to the filled glyphs.
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(glyphs=sorted(glyphs), unicodes=sorted({ord(c) for name in names for c in name}))
    subsetter.subset(font)
    font.save(OUT)
    print(f"{len(names)} icons, {OUT.stat().st_size // 1024} KB: {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
