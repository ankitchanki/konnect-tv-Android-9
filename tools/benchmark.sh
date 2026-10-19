#!/system/bin/sh
# KonnectTV benchmark: the TV's own home vs Glowlauncher after clearing background apps.
# Runs on the TV. Needs Glowlauncher, Netflix and Prime Video installed.
#   adb push tools/benchmark.sh /data/local/tmp/ && adb shell sh /data/local/tmp/benchmark.sh
# Each line: free memory (MemAvailable), home screen memory (PSS), and cold start time
# (am start -W -S: the app is killed first, TotalTime is until its first frame is drawn).
avail() { grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2; }
pss() { dumpsys meminfo $1 | grep "TOTAL PSS" | tr -s ' ' | cut -d' ' -f4; }
cold() { am force-stop $1; sleep 2; am start -W -S -n $2 2>/dev/null | grep TotalTime | cut -d' ' -f2; sleep 5; am force-stop $1; }
NF=com.netflix.ninja/.MainActivity
PV=com.amazon.amazonvideo.livingroom/com.amazon.ignition.IgnitionActivity
LX=com.google.android.apps.tv.launcherx/.home.HomeActivity
GL=io.konnecttv.glowlauncher/.HomeActivity
phase() {
  for r in 1 2 3; do
    am start -n $2 >/dev/null; sleep 20
    a=$(avail); p=$(pss ${2%%/*})
    n=$(cold com.netflix.ninja $NF)
    am start -n $2 >/dev/null; sleep 10
    v=$(cold com.amazon.amazonvideo.livingroom $PV)
    echo "$1 run$r free_mb=$((a/1024)) home_mb=$((p/1024)) netflix_ms=$n prime_ms=$v"
  done
}
# Before: Google TV's home, apps in the background as they were.
phase BEFORE $LX
# After: Glowlauncher, Google TV's home closed, background apps cleared (what Boost does).
am start -n $GL >/dev/null; sleep 3
am force-stop com.google.android.apps.tv.launcherx; am kill-all; sleep 5
phase AFTER $GL
