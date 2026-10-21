# Remote, family setup and access away from home

Konnect TV Plus 2.3.1 and Glowlauncher 1.3.1 both require Android 9/API 28 or later. Upgrade installs retain existing launcher apps, wallpaper and settings when signed with the same certificate.

## Phone feedback and one-handed use

Settings → Phone haptics controls app button feedback, pairing/connection success and completed volume/seek changes. Hardware vibration must exist. Button haptics also follow Android system haptic settings. There is no vibration on an unsuccessful pairing/connection. Settings → One-handed Remote keeps a compact 220dp pad lower on the page; use the bottom Controls sheet for typing, Menu, TV settings, mode switching and messages. Short screens scroll.

## Touchpad and TV overlays

Directional mode translates central swipes into D-pad presses; OK selects the focused item. Cursor mode needs **Settings → Enable/update TV touchpad access**, with an explicit disclosure first. It updates the bundled TV companion and enables its separate accessibility service. It does not select Glowlauncher as Home, intercept Home, read screen contents or open a network listener. Tap the touchpad's mode label to switch between directional and visible cursor modes. Cursor motion is relative, and centre OK taps at its position. The cursor disappears after inactivity.

The left 18% edge adjusts **screen dimming**, with upward swipes increasing displayed brightness. This is a black accessibility overlay, not panel/backlight control. Values are bounded to 20–100% and are reset on screen-off, screensaver start and service connection/restart. Glowlauncher Settings places Undo screen dimming first while dimmed. The right edge changes exposed media volume when supported; otherwise it sends Volume Up/Down keys every approximately 24dp with a haptic tick. Absolute-volume changes apply when the gesture finishes; key fallback applies during travel. Hardware/HDMI volume support still depends on TV firmware.

YouTube, JioHotstar and Cloudstream are quick-launch shortcuts, not special input integrations. TV-only players often support D-pad navigation better than pointer taps. Firmware, DRM surfaces and individual apps can reject gestures/overlays. Seeking still requires a finite duration and ACTION_SEEK_TO from the active media session; there is no blind coordinate-seek fallback.

Remote → Controls → Send TV message shows a temporary mint-bordered bubble over the current TV app for about 6.5 seconds. Everyone watching sees it. Text is limited to 240 characters, is not persisted, and command/response payloads are excluded from diagnostic logs. This requires TV accessibility access, not Android notification-listener access. Revoking TV touchpad access removes cursor, dimming and bubbles while leaving other accessibility services intact.

In **Glowlauncher → Settings → phone privacy**, the TV viewer can block overlays or stop sharing playback titles. The phone cannot silently override these TV-local privacy switches. Android TV settings and Accessibility settings remain available from that menu. Playback information is read only through the existing authorized media-session path; no screen recording or remote camera/microphone is included.

## TV message settings and delivery

While connected, open phone **Settings → TV message bubbles**. Configuration is stored in the TV companion's SharedPreferences, never in a phone-only preference. Each phone reads and edits the same TV rules:

- Show messages on TV: on by default.
- Where to show: Over any app (default), Only on the TV home screen (Glowlauncher), or Only over selected apps.
- The selected-apps list contains launchable apps reported by the TV with their IconDump icons, plus a TV home screen entry. Unavailable icons use a monogram.

The TV owner's overlays privacy switch remains above these rules. The input service tracks only package names from window-state events and retrieves no screen content. Nonmatching apps, disabled messages and TV privacy blocking return a reason to the phone. Queued messages are rechecked if the foreground/rules change; blocked delivery events are reported back while Remote is active. Messages are transient in memory, sequential, with 250ms fades; their text is never persisted or included in diagnostics.

Phone dictation and the Mic key have been removed in 2.3.1. Use Controls → Type on TV for the focused TV search/text field. Unicode entry remains limited by Android's input command and TV IME. There is no phone-audio forwarding or wake-word integration.

## A second phone in the home

Settings → Share setup file opens Android's sharesheet. Choose a trusted recipient with Quick Share or another private transfer. On the recipient, Settings → Import setup file → review → confirm. The file includes saved TV names, tags, hosts, connection ports and the three Remote preferences. Existing saved records are kept. Imports are schema/range/size checked (32KB, at most 30 TVs).

ADB private keys/certificates, pairing codes, grants, undo history, logs and wallpaper images are never included. Each new phone still needs the TV's debugging approval or its own wireless-debugging pairing. Being on the same Wi-Fi does not confer TV control. Once the TV companion is set up, it is already present for every authorized phone. The transfer does not re-run debloating, restore another phone's undo history or automatically connect.

## Mobile data / work network

Mobile data can reach the TV through a **private VPN** linking both devices or the phone and home network. Install/configure Tailscale or WireGuard on the phone and the Android TV (or a home gateway). Pair and authorize the phone at home first. In Konnect TV Plus, enter the TV VPN IP and its current ADB connection port manually; mDNS discovery generally does not cross VPNs. TV must be awake/online and its ADB service reachable over that VPN. Some firmware restricts it to Wi-Fi or changes ports after reboot.

This release contains no embedded VPN client, account backend or cloud relay. It has not been tested across a physical TV/VPN pair. A public mobile-data connection by itself cannot reach a private home Wi-Fi IP. Do not expose or forward an ADB port to the public internet. Over a working private VPN, the same authorized status and bubble commands can be used; there is no background viewing/monitoring service.

## Future hardware options (not implemented in this release)

A phone with a real consumer IR emitter can use Android ConsumerIrManager and brand/model-specific code profiles for power, volume, mute and input. An installed IR app alone does not establish hardware support. IR is one-way, usually requires line of sight and cannot report playback or install a launcher.

Bluetooth pairing alone does not expose the phone microphone to a TV. Ordinary Android apps cannot reliably make the phone advertise itself as the TV's HFP microphone. Supported TVs can instead use a directly paired voice remote/headset, or a future custom companion protocol could handle voice commands separately; that would not become the system Google Assistant microphone automatically.
