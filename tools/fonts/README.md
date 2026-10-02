# The icon font

Culvery draws its icons with Material Symbols Rounded (Apache 2.0, `core/ui/licenses/Apache-MaterialSymbols.txt`) by ligature: `HhIcon(Icons.HOME)` types "home" in the font. The bundled `core/ui/src/main/res/font/material_symbols_rounded.ttf` is a subset holding only the glyphs in `icons.txt` and their filled alternates (what `HhIcon(filled = true)` draws), with all four axes (FILL, GRAD, opsz, wght): about 160 KB instead of 15 MB. The build never runs Python; the subset is committed.

## Adding an icon

1. Find its ligature name at <https://fonts.google.com/icons> (Material Symbols, Rounded).
2. Add a `const val` to `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Icons.kt` and the same name to `icons.txt`.
3. Get the full font. The one Culvery shipped before 4c is in the repo's history:
   ```bash
   git cat-file blob 608ce00:core/ui/src/main/res/font/material_symbols_rounded.ttf > "$TMP/material-symbols-full.ttf"
   ```
   A glyph newer than that font needs Google's current `MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` from <https://github.com/google/material-design-icons/tree/master/variablefont>.
4. With fontTools installed (`python -m pip install --user fonttools`), from the repo root:
   ```bash
   python tools/fonts/subset.py --source "$TMP/material-symbols-full.ttf"
   ```
   It stops and names any icon the source font has no ligature for.
5. `./gradlew :core:ui:testDebugUnitTest --tests "*IconFontTest*"`: every name in `Icons` must be a ligature in the committed font with its filled form (unless IconFontTest lists it in `NO_FILLED_FORM`), and `icons.txt` must match `Icons`.
