#!/usr/bin/env bash
set -eu

test_status=0
./gradlew :glowlauncher:installRelease :app:connectedDebugAndroidTest || test_status=$?

# Collect diagnostics while the emulator is alive, even when a test fails.
mkdir -p ui-previews
for name in wallpaper-editor.png saved-tvs.png wallpaper-failure.png diagnostic-logs.png already-off.png remote-sliders.png remote-buttons.png media-setup.png; do
    adb pull "/data/local/tmp/konnecttv-$name" "ui-previews/$name" >/dev/null 2>&1 || true
done
adb logcat -d -s WallpaperTest:I AndroidRuntime:E > ui-previews/wallpaper-logcat.txt
adb shell dumpsys activity activities > ui-previews/activity-state.txt

if [ "$test_status" -eq 0 ]; then
    test -s ui-previews/wallpaper-editor.png
    test -s ui-previews/saved-tvs.png
fi
exit "$test_status"
