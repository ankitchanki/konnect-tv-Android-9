# Konnect TV Plus UI captures

These PNGs are unmodified captures of the real Android UI, using controlled sample data. They are not generated mockups, physical-TV certification or proof of support in every streaming app.

- App: Konnect TV Plus 2.1.0 (`io.konnecttv.plus`), bundled Glowlauncher 1.2.0.
- Tested source: [`16e77690b3435f875d9c2452e9e63af9909fda5e`](https://github.com/ankitchanki/konnect-tv-Android-9/commit/16e77690b3435f875d9c2452e9e63af9909fda5e).
- [Successful build/runtime run](https://github.com/ankitchanki/konnect-tv-Android-9/actions/runs/39569822179), artifacts **Android-API-28-smoke-results** and **Android-API-36-smoke-results**, directory `ui-previews/`.
- Emulator profile: Nexus 6. API 28 is Android 9; API 36 is Android 16.

| File | Android API | Capture / sample data |
|:--|:--|:--|
| `remote-sliders.png` | 28 | `RemoteMediaTest.currentRemoteScreenshotsIncludeTheNewSlidersAndOriginalButtons`; upper Remote panel, sample four-minute video at 1:00 and volume 40%. |
| `remote-buttons.png` | 36 | Same test; lower Remote panel with the original button rows visible. |
| `media-setup.png` | 28 | `RemoteMediaTest.mediaAccessSetupRequiresAnExplicitTap`; initial disabled state and access disclosure; no permission is granted by this screenshot fixture. |
| `saved-tvs.png` | 28 | `Android9SmokeTest.savedTvCanBeNamedTaggedAndReusedWithoutTypingTheIp`; sample Living room / Friend's TV and local address. |
| `wallpaper-editor.png` | 28 | `Android9SmokeTest.wallpaperEditorOffersFitAndCropOnAndroid9`; gradient fixture, fit mode and 16:9 TV preview. |
| `diagnostic-logs.png` | 28 | `PlusDiagnosticsTest.firstLaunchLogsWorkWithoutATvAndPersistRedactedErrors`; deliberately recorded installation failure with redacted sample values. |
| `already-off.png` | 28 | `PlusDiagnosticsTest.alreadyDisabledAndOwnerRemovedAppsOfferRecoveryWithoutHistory`; simulated TCL identity/package states and recovery controls. |

Remote, setup and Detox captures isolate the actual control components; their fixtures do not mount the main app's floating tab bar. The full connected app has Remote, Apps, Detox, Boost and Settings tabs. Images are kept intact, including native Android system bars. No real TV or account information appears in these fixtures.

Runtime integration tests separately exercise an actual Android media session, absolute seek and volume changes, stale-media refusal, caller restrictions and revoked access. The sample player shown in the gallery is only a UI fixture.

To refresh, copy the listed PNGs from a successful emulator run's `ui-previews/` artifacts and update the source, OS and run references here.

The earlier Remote/Apps/Detox/Boost images and Glowlauncher animation in the parent directory are upstream KonnectTV assets, retained with attribution in the root README.
