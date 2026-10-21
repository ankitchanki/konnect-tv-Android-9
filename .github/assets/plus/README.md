# Konnect TV Plus 2.3.1 UI captures

These PNGs are unmodified real emulator captures with controlled sample data. They do not certify physical-TV or streaming-app compatibility.

- Phone 2.3.1 (`io.konnecttv.plus`), bundled Glowlauncher 1.3.1; Android 9/API 28.
- Tested source: [`814ed8d9d4c5e0a88dc8959303f6eb0a7e9212b9`](https://github.com/ankitchanki/konnect-tv-Android-9/commit/814ed8d9d4c5e0a88dc8959303f6eb0a7e9212b9).
- [Full build/API 28 and 36 run](https://github.com/ankitchanki/konnect-tv-Android-9/actions/runs/39593872983). Images come from its **Android-API-28-smoke-results** artifact, `ui-previews/`.
- Nexus 6, 1440×2560 pixels, density overridden to 480dpi (~480×853dp). Small Remote fixture constrains itself to 360×640dp. Native system bars and any area outside the fixture remain intact.

| File | Fixture |
|:--|:--|
| `remote-sliders.png` | `RemoteMediaTest.tallRemoteFillsTheSpaceAboveTheTabBar`: full Remote with real floating tabs, four-minute sample video at 1:00, volume 40%; JioHotstar, Cloudstream and Menu keys. |
| `remote-buttons.png` | `RemoteMediaTest.smallRemoteKeepsMinimumDpadAndScrollsToBottomKeys`: 360×640dp viewport, scrolled to bottom keys. |
| `touchpad.png` | `RemoteInputTest.touchpadAndCompactModeKeepAnOkButtonAndCurrentShortcuts`: compact one-handed touchpad; disconnected transport and unavailable media, without floating tabs. |
| `media-setup.png` | `RemoteMediaTest.mediaAccessSetupRequiresAnExplicitTap`: setup disclosure, no automatic grant. |
| `saved-tvs.png` | `Android9SmokeTest.savedTvCanBeNamedTaggedAndReusedWithoutTypingTheIp`: sample Living room / Friend's TV. |
| `wallpaper-editor.png` | `Android9SmokeTest.wallpaperEditorOffersFitAndCropOnAndroid9`: gradient photo, fit/crop controls and 16:9 TV preview. |
| `diagnostic-logs.png` | `PlusDiagnosticsTest.firstLaunchLogsWorkWithoutATvAndPersistRedactedErrors`: deliberate redacted sample installation failure. |
| `already-off.png` | `PlusDiagnosticsTest.alreadyDisabledAndOwnerRemovedAppsOfferRecoveryWithoutHistory`: simulated TCL identity and package state. |

Setup and Detox fixtures isolate their actual components. D-pad Remote fixtures mount the actual Remote and main floating tab bar together. Touchpad fixture mounts its controls and bottom-sheet host; it does not mount the tab bar. No real household information is shown. Streaming-app shortcuts use monogram fallbacks here because these apps are not installed on the emulator; on a connected TV, IconDump supplies the actual selected package icons.

Separate runtime tests exercise an actual media session and real companion accessibility overlays (cursor, dimming and automatic bubble expiry), caller restrictions and revoked media access. These are emulator tests, not physical-TV or streaming-account tests. Phone vibration hardware still needs physical-phone verification. Mic/dictation is removed. Bubble queues/rules and screen-off dim reset are tested separately against the real TV companion on each emulator.

Upstream Remote/Apps/Detox/Boost images and the Glowlauncher animation in the parent directory remain attributed reference material.
