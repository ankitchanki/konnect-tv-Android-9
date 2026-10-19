<p align="center"><img src=".github/assets/icon.png" width="112" alt="KonnectTV logo"></p>

<h1 align="center">KonnectTV</h1>

<p align="center"><img src=".github/assets/poster.png" alt="KonnectTV: make your old TV feel new again"></p>

**Make your old Android TV feel new again, from your phone.** Turn off the bloat, free up memory, swap the cluttered home screen for a clean glowing one, and control everything with a remote on your phone.

<p align="center"><img src=".github/assets/glowlauncher.gif" width="720" alt="Glowlauncher on a TV: a big clock and a dock of apps with a glow that fades through colours as focus moves"></p>

<p align="center">
  <img src=".github/assets/remote.jpg" width="180" alt="KonnectTV remote with d-pad and symbol buttons">
  <img src=".github/assets/apps.jpg" width="180" alt="KonnectTV app manager with real app icons">
  <img src=".github/assets/detox.jpg" width="180" alt="KonnectTV Detox list of apps safe to turn off">
  <img src=".github/assets/boost.jpg" width="180" alt="KonnectTV Boost with RAM and storage rings and the Glowlauncher orb">
</p>

<p align="center"><a href="https://github.com/yashoncode/konnect-tv/releases/latest"><b>⬇ Download the latest KonnectTV</b></a></p>

## Features

- ✨ **Glowlauncher.** A calm home screen for your TV: a big clock and only the apps you pick, in one glowing dock. Choose the glow colour, or RGB to fade through all of them.
- 🧹 **Detox.** One tap turns off sponsored rows, screensavers and promo apps. Apps are turned off, never deleted, and every change can be undone.
- ⚡ **Boost.** See free RAM and storage, turn off animations so menus open instantly, stop the screensaver, set a sleep timer and clear background apps.
- 🎮 **Remote.** D-pad, OK, back, home, volume, mute, power and play/pause, with haptics on every tap. Type on the TV with your phone keyboard.
- 📦 **App manager.** Every app on the TV with its real icon and name. Open, force stop, clear data, turn off or on, uninstall your own apps, and install an APK from your phone.
- 📺 **Many TVs.** Finds Android TVs on your Wi-Fi by itself and remembers them. Switch between TVs from the top bar.
- 🌗 **Light and dark.** One tap on the top bar.
- 🔒 **Private.** No account, no ads, no tracking. Everything stays on your phone and your TV.

## Install

Download `KonnectTV.apk` from the [latest release](https://github.com/yashoncode/konnect-tv/releases/latest) and open it on your phone. The phone must run Android 12 or newer.

## Set up the TV (once)

1. On the TV, open **Settings › Device Preferences › About** (or **System › About**).
2. Press OK on **Build** 7 times until it says you are a developer.
3. Go back to **Developer options** and turn on **USB debugging**. If you see **Wireless debugging** or **Network debugging**, turn that on too.

## Connect

Put the phone and the TV on the same Wi-Fi and open KonnectTV. It finds Android TVs on the network by itself.

- **TVs with Wireless debugging** (Android 11 and newer): on the TV, open **Wireless debugging › Pair device with pairing code**. Tap the TV in KonnectTV and type the 6-digit code.
- **Other TVs**: tap the TV. It asks **"Allow debugging?"**. Tick **Always allow** and press **Allow**.
- **TV not listed?** Tap **Enter IP address manually**. The IP is under the TV's network settings. The port is 5555, or the one shown under Wireless debugging.

After the first time, KonnectTV remembers the TV and reconnects in one tap.

## Safe by design

- Apps are **turned off** (`pm disable-user`), never deleted. Undo one change or everything from **Detox › History**.
- KonnectTV never turns off Google Play, Play services, keyboards, Bluetooth, HDMI/TV inputs, Settings or the system UI. This applies to Detox and the app manager.
- Only apps you installed yourself can be uninstalled. System apps can only be turned off.
- Glowlauncher only turns off other launchers and home screens. It never turns off the TV maker's Live TV or input app, even when that app also answers the Home button.
- The safe-to-turn-off list is in [`app/src/main/assets/debloat.json`](app/src/main/assets/debloat.json). Each entry has a plain-English reason. Pull requests for more brands are welcome.

Works on Android TV and Google TV: Sony, TCL, Hisense, Philips, Xiaomi, Chromecast with Google TV, NVIDIA Shield and others. Samsung (Tizen), LG (webOS) and Roku don't run Android, so they can't be cleaned this way.

## Glowlauncher

Glowlauncher is KonnectTV's own home screen for the TV: a big clock and the apps you pick in one glowing dock, with Inputs, Settings and network at the top right. It ships inside KonnectTV (about 45 KB), so nothing is downloaded. On the TV, hold OK or press Menu on an app to move or remove it. The **+** tile adds one. Turn it off from **Boost** to get the TV's own home back.

**Turning it on can freeze low-memory TVs for a minute or two.** Changing the home screen is the heaviest thing a TV does. KonnectTV clears background apps first and shows each step on your phone while it works. If the TV freezes, leave it on and don't unplug it. It recovers by itself.

> Use at your own risk. This is what works on the TVs we tried. It is not professional advice.

## Build it yourself

You need JDK 17+ and the Android SDK (platform 37).

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The app uses Kotlin and Jetpack Compose. Glowlauncher (`glowlauncher/`) uses plain Android views so it stays small and runs on Android TV 5.0 and newer. ADB runs on [libadb-android](https://github.com/MuntashirAkon/libadb-android), which handles pairing and TLS. The frosted glass uses [Haze](https://github.com/chrisbanes/haze).

To sign a release build, create `keystore.properties` in the project root. Never commit it.

```properties
storeFile=konnecttv.jks
storePassword=...
keyAlias=konnecttv
keyPassword=...
```

## Credits

Inspired by [TV Detox](https://github.com/Ansub/tv-detox). Glowlauncher is based on TV Detox's home screen.

## Support KonnectTV

KonnectTV is free, has no ads and always will. I build it in my spare time because I love using it.
If it made your TV a little faster and you'd like to say thanks, you can buy me a coffee.
It's completely optional, and every coffee is truly appreciated. ☕

<p align="center"><a href="https://buymeacoffee.com/yashwynth"><img src="https://img.shields.io/badge/Buy%20me%20a%20coffee-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=000000" alt="Buy me a coffee"></a></p>

A star on GitHub or sharing KonnectTV with a friend helps just as much. Thank you for using it! ❤️

## License

MIT
