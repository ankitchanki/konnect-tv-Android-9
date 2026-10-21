# Konnect TV Plus UI captures

These PNGs are unmodified captures of the real Android UI, using controlled sample data. They are not generated mockups, physical-TV certification or proof of support in every streaming app.

- Remote captures: Konnect TV Plus 2.2.0 (`io.konnecttv.plus`), bundled Glowlauncher 1.2.0. Other images remain from the 2.1.0 run listed below.
- Original 2.1.0 tested source: [`16e77690b3435f875d9c2452e9e63af9909fda5e`](https://github.com/ankitchanki/konnect-tv-Android-9/commit/16e77690b3435f875d9c2452e9e63af9909fda5e).
- [Original 2.1.0 build/runtime run](https://github.com/ankitchanki/konnect-tv-Android-9/actions/runs/39569822179), artifacts **Android-API-28-smoke-results** and **Android-API-36-smoke-results**, directory `ui-previews/`.
- 2.2.0 tested source: [`f8846302f732dba5946b13de65f518c63d8d5ff1`](https://github.com/ankitchanki/konnect-tv-Android-9/commit/f8846302f732dba5946b13de65f518c63d8d5ff1). [Full build and API 28/36 run](https://github.com/ankitchanki/konnect-tv-Android-9/actions/runs/39587443398). Remote images come from its **Android-API-28-smoke-results** artifact.
- Emulator profile: Nexus 6. The 2.2.0 run sets density to 480dpi for a ~480×853dp tall screen; its small-screen fixture uses a 360×640dp viewport. API 28 is Android 9; API 36 is Android 16.

| File | Android API | Capture / sample data |
|:--|:--|:--|
| `remote-sliders.png` | 28 | 2.2.0: `RemoteMediaTest.tallRemoteFillsTheSpaceAboveTheTabBar`; Nexus 6 full Remote with floating tabs, sample four-minute video at 1:00 and volume 40%. |
| `remote-buttons.png` | 28 | 2.2.0: `RemoteMediaTest.smallRemoteKeepsMinimumDpadAndScrollsToBottomKeys`; 360 × 640dp viewport, scrolled to the bottom; surrounding emulator area/system bars remain intact. |
| `media-setup.png` | 28 | `RemoteMediaTest.mediaAccessSetupRequiresAnExplicitTap`; initial disabled state and access disclosure; no permission is granted by this screenshot fixture. |
| `saved-tvs.png` | 28 | `Android9SmokeTest.savedTvCanBeNamedTaggedAndReusedWithoutTypingTheIp`; sample Living room / Friend's TV and local address. |
| `wallpaper-editor.png` | 28 | `Android9SmokeTest.wallpaperEditorOffersFitAndCropOnAndroid9`; gradient fixture, fit mode and 16:9 TV preview. |
| `diagnostic-logs.png` | 28 | `PlusDiagnosticsTest.firstLaunchLogsWorkWithoutATvAndPersistRedactedErrors`; deliberately recorded installation failure with redacted sample values. |
| `already-off.png` | 28 | `PlusDiagnosticsTest.alreadyDisabledAndOwnerRemovedAppsOfferRecoveryWithoutHistory`; simulated TCL identity/package states and recovery controls. |

The 2.2.0 Remote fixtures mount the actual Remote and floating tab bar together, with sample playback and a disconnected ADB transport. Setup and Detox captures isolate their control components. The full connected app has Remote, Apps, Detox, Boost and Settings tabs. Images are kept intact, including native Android system bars. No real TV or account information appears in these fixtures.

Runtime integration tests separately exercise an actual Android media session, absolute seek and volume changes, stale-media refusal, caller restrictions and revoked access. The sample player shown in the gallery is only a UI fixture.

To refresh, copy the listed PNGs from a successful emulator run's `ui-previews/` artifacts and update the source, OS and run references here.

The earlier Remote/Apps/Detox/Boost images and Glowlauncher animation in the parent directory are upstream KonnectTV assets, retained with attribution in the root README.
