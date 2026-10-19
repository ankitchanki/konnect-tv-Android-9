# Benchmark

How the numbers in the README were measured, with the raw output so anyone can check them.

## Device

| | |
|:--|:--|
| TV | CHiQ AI PONT (GTVU65), Google TV |
| Android | 14 (SDK 34), build `CHiQ/GTVU65/dangni:14/UKN6.260116.054/15121168` |
| Memory | 952,692 kB (about 0.9 GB) |
| Storage | 4 GB |
| Date | 19 October 2026 |

## Method

The script is [`tools/benchmark.sh`](../tools/benchmark.sh). It runs on the TV over ADB and does each step 3 times:

1. Open the home screen and wait 20 seconds for it to settle.
2. Read **free memory** (`MemAvailable` in `/proc/meminfo`) and the **home screen's memory** (`TOTAL PSS` from `dumpsys meminfo`).
3. **Cold start** Netflix: kill it, then `am start -W -S` and read `TotalTime`, the time until the app draws its first frame.
4. Go home, wait 10 seconds, and cold start Prime Video the same way.

**Before** is Google TV's own home (`launcherx`) with apps left in the background, the way the TV normally runs.
**After** is Glowlauncher, with Google TV's home closed and background apps cleared (`am kill-all`), which is what KonnectTV's Boost does.

## Raw output

```
BEFORE run1 free_mb=200 home_mb=76 netflix_ms=2131 prime_ms=3416
BEFORE run2 free_mb=273 home_mb=80 netflix_ms=1718 prime_ms=2707
BEFORE run3 free_mb=255 home_mb=89 netflix_ms=1408 prime_ms=3370
AFTER run1 free_mb=357 home_mb=41 netflix_ms=828 prime_ms=3009
AFTER run2 free_mb=299 home_mb=21 netflix_ms=946 prime_ms=1493
AFTER run3 free_mb=308 home_mb=19 netflix_ms=851 prime_ms=1867
```

## Results (median of 3)

| | Before | After | Change |
|:--|--:|--:|:--|
| Netflix cold start | 1718 ms | 851 ms | 2.0× faster |
| Prime Video cold start | 3370 ms | 1867 ms | 1.8× faster |
| Home screen memory | 80 MB | 21 MB | 3.8× lighter |
| Free memory | 255 MB | 308 MB | +53 MB |

Medians: Netflix 1408 / **1718** / 2131 → 828 / **851** / 946. Prime 2707 / **3370** / 3416 → 1493 / **1867** / 3009. Home 76 / **80** / 89 → 19 / **21** / 41. Free 200 / **255** / 273 → 299 / **308** / 357.

## Limits

- One TV, 3 runs each. Times vary from run to run (Netflix before: 1.4 to 2.1 s), so treat the numbers as a guide, not a promise.
- Glowlauncher's first run (41 MB) includes loading app icons; it settles near 20 MB.
- An earlier attempt was thrown away: Glowlauncher was turned off on the phone halfway through, so its "after" runs never opened Glowlauncher.
- Your TV will differ. Older, fuller TVs gain the most.

Run `tools/benchmark.sh` on your own TV and open an issue with your results.
