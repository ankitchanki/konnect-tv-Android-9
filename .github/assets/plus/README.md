# Konnect TV Plus UI captures

These PNGs are unmodified screenshots of the real app captured by its Android UI tests, using sample data. They are not generated mockups or physical-TV certification.

- App version: Konnect TV Plus 2.0.0 (`io.konnecttv.plus`).
- Source commit: [`7651ca9c5c379d623663378a6f53d2ded2eaf19f`](https://github.com/ankitchanki/konnect-tv-Android-9/commit/7651ca9c5c379d623663378a6f53d2ded2eaf19f).
- Capture environment: Android 9 / API 28 emulator, Nexus 6 profile.
- [Successful CI run](https://github.com/ankitchanki/konnect-tv-Android-9/actions/runs/39553747135), artifact **Android-API-28-smoke-results**, directory `ui-previews/`.

| File | Capture | Sample data |
|:--|:--|:--|
| `saved-tvs.png` | `Android9SmokeTest.savedTvCanBeNamedTaggedAndReusedWithoutTypingTheIp` | Living room, Friend's TV tag and an example local address; no real TV connection. |
| `wallpaper-editor.png` | `Android9SmokeTest.wallpaperEditorOffersFitAndCropOnAndroid9` | Generated gradient fixture, fit mode and a 16:9 TV preview. |
| `diagnostic-logs.png` | `PlusDiagnosticsTest.firstLaunchLogsWorkWithoutATvAndPersistRedactedErrors` | Deliberately recorded installation error; example address, token and pairing code redacted by the app. |
| `already-off.png` | `PlusDiagnosticsTest.alreadyDisabledAndOwnerRemovedAppsOfferRecoveryWithoutHistory` | Simulated TCL identity and two package states; shows recovery UI without modifying a TV. |

To refresh, run the emulator workflow and copy these four PNGs from the successful run's `ui-previews/` artifact. Keep screenshots intact and update this provenance when the source version or capture changes.

The earlier Remote/Apps/Detox/Boost images and Glowlauncher animation in the parent directory are upstream KonnectTV assets, retained with attribution in the root README.
