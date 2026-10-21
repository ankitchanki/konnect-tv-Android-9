# Konnect TV Plus UI design · SVG

Vector mockups of every phone screen and sheet in Konnect TV Plus, plus the Glowlauncher TV home. They were drawn from the Compose source (`app/.../ui/*.kt`, `MainActivity.kt`) and `glowlauncher/.../HomeActivity.kt`, using the "mono matte" tokens in `ui/Theme.kt`.

- [`konnect-tv-plus-all-screens.svg`](konnect-tv-plus-all-screens.svg): all 32 phone screens and sheets on one sheet
- [`glowlauncher-all-screens.svg`](glowlauncher-all-screens.svg): 6 TV states, shown at 50 %
- [`components.svg`](components.svg): dark and light tokens, buttons, toggle, check, chips, sliders, tiles, glass tab bar, pairing code, glow accents and type scale
- [`phone/`](phone): one 390 × 844 dp SVG per screen
- [`tv/`](tv): one 1920 × 1080 SVG per Glowlauncher state

**Phone:** Connect (busy and first run), Pair with code, Connect by IP, Edit saved TV, Remote (dark, light and media-error), Type on TV, TV menu, status toast, Apps, App actions, Disabled app, Detox (top and lower groups), Undo history, Boost (off, on and tweaks), Set up Glowlauncher, setup progress (running, done and error), Sleep after, Glowlauncher menu, Remove wallpaper, Wallpaper editor, Settings (tab and before connecting), Diagnostic logs, Clear logs.

**TV:** home, custom wallpaper with the Sky glow, move mode, app menu, Add an app, Inputs.

The content is sample data, and brand-coloured app tiles stand in for real app icons. The files are plain SVG with no scripts, so they open in Figma, Illustrator, Inkscape or a browser.

## Regenerate

```bash
cd docs/design/generator
python3 konnect.py ..
```
