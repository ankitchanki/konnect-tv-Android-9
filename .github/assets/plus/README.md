# Konnect TV Plus 2.3.0 UI captures

These PNGs are unmodified real emulator captures with controlled sample data. They do not certify physical-TV or streaming-app compatibility.

- Phone 2.3.0 (`io.konnecttv.plus`), bundled Glowlauncher 1.3.0; Android 9/API 28.
- Tested source: [`5df04d3f694acbb67ca15ba23b3e70e6484fe38c`](https://github.com/ankitchanki/konnect-tv-Android-9/commit/5df04d3f694acbb67ca15ba23b3e70e6484fe38c).
- [Full build/API 28 and 36 run](https://github.com/ankitchanki/konnect-tv-Android-9/actions/runs/39590570996). Images come from its **Android-API-28-smoke-results** artifact, `ui-previews/`.
- Nexus 6, 1440×2560 pixels, density overridden to 480dpi (~480×853dp). Small Remote fixture constrains itself to 360×640dp. Native system bars and any area outside the fixture remain intact.

| File | Fixture |
|:--|:--|
| `remote-sliders.png` | `RemoteMediaTest.tallRemoteFillsTheSpaceAboveTheTabBar`: full Remote with real floating tabs, four-minute sample video at 1:00, volume 40%; JioHotstar, Cloudstream and Mic keys. |
| `remote-buttons.png` | `RemoteMediaTest.smallRemoteKeepsMinimumDpadAndScrollsToBottomKeys`: 360×640dp viewport, scrolled to bottom keys. |
| `touchpad.png` | `RemoteInputTest.touchpadAndCompactModeKeepAnOkButtonAndCurrentShortcuts`: compact one-handed touchpad; disconnected transport and unavailable media, without floating tabs. |
| `media-setup.png` | `RemoteMediaTest.mediaAccessSetupRequiresAnExplicitTap`: setup disclosure, no automatic grant. |
| `saved-tvs.png` | `Android9SmokeTest.savedTvCanBeNamedTaggedAndReusedWithoutTypingTheIp`: sample Living room / Friend's TV. |
| `wallpaper-editor.png` | `Android9SmokeTest.wallpaperEditorOffersFitAndCropOnAndroid9`: gradient photo, fit/crop controls and 16:9 TV preview. |
| `diagnostic-logs.png` | `PlusDiagnosticsTest.firstLaunchLogsWorkWithoutATvAndPersistRedactedErrors`: deliberate redacted sample installation failure. |
| `already-off.png` | `PlusDiagnosticsTest.alreadyDisabledAndOwnerRemovedAppsOfferRecoveryWithoutHistory`: simulated TCL identity and package state. |

Setup and Detox fixtures isolate their actual components. D-pad Remote fixtures mount the actual Remote and main floating tab bar together. Touchpad fixture mounts its controls and bottom-sheet host; it does not mount the tab bar. No real household information is shown.

Separate runtime tests exercise an actual media session and real companion accessibility overlays (cursor, dimming and automatic bubble expiry), caller restrictions and revoked media access. These are emulator tests, not physical-TV or streaming-account tests. Phone vibration hardware and speech-service availability still need physical-phone verification.

Upstream Remote/Apps/Detox/Boost images and the Glowlauncher animation in the parent directory remain attributed reference material.
