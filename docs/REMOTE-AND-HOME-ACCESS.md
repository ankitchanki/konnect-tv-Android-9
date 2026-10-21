# Remote, family setup and access away from home

Konnect TV Plus 2.3.0 and Glowlauncher 1.3.0 both require Android 9/API 28 or later. Upgrade installs retain existing launcher apps, wallpaper and settings when signed with the same certificate.

## Phone feedback and one-handed use

Settings → Phone haptics controls app button feedback, pairing/connection success and completed volume/seek changes. Hardware vibration must exist. Button haptics also follow Android system haptic settings. There is no vibration on an unsuccessful pairing/connection. Settings → One-handed Remote keeps a compact 220dp pad lower on the page; use the bottom Controls sheet for typing, Menu, TV settings, mode switching and messages. Short screens scroll.

## Touchpad and TV overlays

Directional mode translates central swipes into D-pad presses; OK selects the focused item. Cursor mode needs **Settings → Enable/update TV touchpad access**, with an explicit disclosure first. It updates the bundled TV companion and enables its separate accessibility service. It does not select Glowlauncher as Home, intercept Home, read screen contents or open a network listener. Tap the touchpad's mode label to switch between directional and visible cursor modes. Cursor motion is relative, and centre OK taps at its position. The cursor disappears after inactivity.

The left 18% edge adjusts **screen dimming**, with upward swipes increasing displayed brightness. This is a black accessibility overlay, not panel/backlight control. Values are bounded to 20–100% and are reset when the service is disabled/restarted. The right edge changes the TV's exposed media volume; enable media sliders first. Edge adjustments apply when the gesture finishes. Fixed HDMI/receiver volume remains unsupported.

YouTube, JioHotstar and Cloudstream are quick-launch shortcuts, not special input integrations. TV-only players often support D-pad navigation better than pointer taps. Firmware, DRM surfaces and individual apps can reject gestures/overlays. Seeking still requires a finite duration and ACTION_SEEK_TO from the active media session; there is no blind coordinate-seek fallback.

Remote → Controls → Send TV message shows a temporary mint-bordered bubble over the current TV app for about 6.5 seconds. Everyone watching sees it. Text is limited to 240 characters, is not persisted, and command/response payloads are excluded from diagnostic logs. This requires TV accessibility access, not Android notification-listener access. Revoking TV touchpad access removes cursor, dimming and bubbles while leaving other accessibility services intact.

In **Glowlauncher → Settings → phone privacy**, the TV viewer can block overlays or stop sharing playback titles. The phone cannot silently override these TV-local privacy switches. Android TV settings and Accessibility settings remain available from that menu. Playback information is read only through the existing authorized media-session path; no screen recording or remote camera/microphone is included.

## Phone microphone

Mic launches the phone's installed speech recognition service, then shows a review field before text is sent to the TV's focused search/text field. The phone's speech service may process audio online and may be unavailable. This app does not record audio itself or implement an always-on “OK Google” wake word. Unicode entry remains limited by Android's `input text` support and the TV IME. The separate TV assistant action opens the TV's assistant, which uses its own TV/remote microphone. Live phone audio is not forwarded as a TV microphone.

## A second phone in the home

Settings → Share setup file opens Android's sharesheet. Choose a trusted recipient with Quick Share or another private transfer. On the recipient, Settings → Import setup file → review → confirm. The file includes saved TV names, tags, hosts, connection ports and the three Remote preferences. Existing saved records are kept. Imports are schema/range/size checked (32KB, at most 30 TVs).

ADB private keys/certificates, pairing codes, grants, undo history, logs and wallpaper images are never included. Each new phone still needs the TV's debugging approval or its own wireless-debugging pairing. Being on the same Wi-Fi does not confer TV control. Once the TV companion is set up, it is already present for every authorized phone. The transfer does not re-run debloating, restore another phone's undo history or automatically connect.

## Mobile data / work network

Mobile data can reach the TV through a **private VPN** linking both devices or the phone and home network. Install/configure Tailscale or WireGuard on the phone and the Android TV (or a home gateway). Pair and authorize the phone at home first. In Konnect TV Plus, enter the TV VPN IP and its current ADB connection port manually; mDNS discovery generally does not cross VPNs. TV must be awake/online and its ADB service reachable over that VPN. Some firmware restricts it to Wi-Fi or changes ports after reboot.

This release contains no embedded VPN client, account backend or cloud relay. It has not been tested across a physical TV/VPN pair. A public mobile-data connection by itself cannot reach a private home Wi-Fi IP. Do not expose or forward an ADB port to the public internet. Over a working private VPN, the same authorized status and bubble commands can be used; there is no background viewing/monitoring service.
