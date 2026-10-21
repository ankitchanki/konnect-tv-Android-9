#!/usr/bin/env bash
set -eu

# Nexus 6 defaults to ~731dp tall at 560dpi, which is a scrolling Remote viewport.
# Use 480dpi (~853dp tall) for the tall fixture; the second fixture constrains itself to 360x640dp.
adb shell wm density 480
mkdir -p ui-previews
adb shell wm size > ui-previews/display-metrics.txt
adb shell wm density >> ui-previews/display-metrics.txt

test_status=0
./gradlew :glowlauncher:installRelease :app:connectedDebugAndroidTest || test_status=$?

# Collect diagnostics while the emulator is alive, even when a test fails.
mkdir -p ui-previews
for name in wallpaper-editor.png saved-tvs.png wallpaper-failure.png diagnostic-logs.png already-off.png remote-sliders.png remote-buttons.png media-setup.png touchpad.png companion-replacement.png; do
    adb pull "/data/local/tmp/konnecttv-$name" "ui-previews/$name" >/dev/null 2>&1 || true
done
adb logcat -d -s WallpaperTest:I AndroidRuntime:E > ui-previews/wallpaper-logcat.txt
adb shell dumpsys activity activities > ui-previews/activity-state.txt

if [ "$test_status" -eq 0 ]; then
    test -s ui-previews/wallpaper-editor.png
    test -s ui-previews/saved-tvs.png
fi
exit "$test_status"
