# KonnectTV

**Make a slow Android TV fast again, from your phone.**

KonnectTV is a small Android app that connects to your Android TV or Google TV over Wi-Fi and lets you:

- **Remote**: d-pad, OK, back, home, volume, mute, power, play/pause, and typing with your phone keyboard.
- **Apps**: see every app on the TV. Open, force stop, clear data, turn off or on, uninstall your own apps, and install an APK from your phone.
- **Detox**: one tap turns off the bloat (sponsored rows, screensavers, promo apps). Apps are turned off, never deleted, and every change can be undone.
- **Boost**: animations off, screensaver off, a sleep timer, kill background apps, free RAM before and after, and an optional clean launcher.

No account, no ads, no tracking. Everything stays on your phone.

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
- The clean launcher only turns off other launchers and home screens. It never turns off the TV maker's Live TV or input app, even when that app also answers the Home button.
- The safe-to-turn-off list is in [`app/src/main/assets/debloat.json`](app/src/main/assets/debloat.json). Each entry has a plain-English reason. Pull requests for more brands are welcome.

Works on Android TV and Google TV: Sony, TCL, Hisense, Philips, Xiaomi, Chromecast with Google TV, NVIDIA Shield and others. Samsung (Tizen), LG (webOS) and Roku don't run Android, so they can't be cleaned this way.

**Turning on the clean launcher can freeze low-memory TVs for a few minutes.** Changing the home screen is the heaviest thing a TV does: it installs an app, changes the Home app and restarts the home screen. TVs with 1–2 GB of memory can stop responding while this happens. KonnectTV clears background apps first to make room. If the TV freezes, leave it on and don't unplug it. It recovers by itself, and the next start is normal.

> Use at your own risk. This is what works on the TVs we tried. It is not professional advice.

## Build it yourself

You need JDK 17+ and the Android SDK (platform 37).

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The app uses Kotlin and Jetpack Compose. ADB runs on [libadb-android](https://github.com/MuntashirAkon/libadb-android), which handles pairing and TLS. The frosted glass uses [Haze](https://github.com/chrisbanes/haze).

To sign a release build, create `keystore.properties` in the project root. Never commit it.

```properties
storeFile=konnecttv.jks
storePassword=...
keyAlias=konnecttv
keyPassword=...
```

## Credits

Inspired by [TV Detox](https://github.com/Ansub/tv-detox). The optional clean launcher is TV Detox's home screen, downloaded from its releases.

## License

MIT
