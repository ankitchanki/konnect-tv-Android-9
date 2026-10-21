# Package catalog and recovery

Reviewed on **2026-10-21**. The executable catalog is [`debloat.json`](../app/src/main/assets/debloat.json). Rules match exact package names found on the connected TV; the manufacturer/model shown in Detox comes from TV properties, with vendor-package evidence as fallback. Generic MediaTek packages alone do not identify TCL.

## Scope and sources

This catalog has 59 optional-package rules (21 TCL) and 78 exact protected packages, plus patterns for essential services. It is **not an exhaustive 2020–2026 TCL product database**. Model year does not establish that disabling a package is safe. Firmware, region, hardware and feature usage can differ even for one model.

Primary evidence is [CleanYourTV's device-owner reports](https://github.com/Gathin23/cleanyourtv/tree/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples), pinned to commit `9071ec8d2f41826778f6b4c7f5f42dadc22f5b22`, and [Android's package-manager documentation](https://developer.android.com/tools/adb#pm). The reports include TCL 55C715 (2020, Android 9), C725, C735, G10 (2023), C755 (2024), QM6K (2025), P7K and G09, and Sony, Xiaomi (including A Pro 2026), Hisense, Philips, NVIDIA and OnePlus devices. These are community reports, not vendor certification or physical testing by this fork. Each added optional rule links its exact source; the complete protected-reason map is in the JSON.

New vendor options start **unchecked**. Existing upstream common Android TV entries are retained separately below. Removing optional content can still remove a feature you use. Unknown system packages appear under **Not reviewed** and cannot be disabled or have data cleared in Plus. Protection wins even if Android classifies the package as a user app.

## Current optional-package rules

| Brand | Exact package | Function and consequence | Evidence |
|:--|:--|:--|:--|
| Android TV | `com.amazon.amazonvideo.livingroom` | Preinstalled. Keep if you watch it | Retained upstream rule |
| Android TV | `com.android.bips` | Printing service. TVs don't print | Retained upstream rule |
| Android TV | `com.android.dreams.basic` | Colour screensaver nobody needs | Retained upstream rule |
| Android TV | `com.android.printspooler` | Printing service. TVs don't print | Retained upstream rule |
| Android TV | `com.google.android.apps.tv.dreamx` | Google TV photo screensaver, eats RAM while idle | Retained upstream rule |
| Android TV | `com.google.android.backdrop` | Photo screensaver | Retained upstream rule |
| Android TV | `com.google.android.feedback` | Sends bug reports to Google | Retained upstream rule |
| Android TV | `com.google.android.leanbacklauncher.recommendations` | Suggested videos row on the older home screen | Retained upstream rule |
| Android TV | `com.google.android.play.games` | Games hub with promos | Retained upstream rule |
| Android TV | `com.google.android.tvrecommendations` | Fills the home screen with sponsored rows | Retained upstream rule |
| Android TV | `com.google.android.videos` | Movie store. Keep if you buy movies from Google | Retained upstream rule |
| Android TV | `com.google.android.youtube.tvmusic` | Keep if you play music on the TV | Retained upstream rule |
| Android TV | `com.nvidia.tegrazone3` | Game store promos | Retained upstream rule |
| Android TV | `com.sony.dtv.sonyselect` | Sponsored app row | Retained upstream rule |
| Hisense | `com.hisense.app.logreport` | Vendor diagnostic reporting. Keep when Hisense support needs logs. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/hisense-hg9/debloat.txt) |
| Hisense | `com.hisense.evservice` | Automatic content recognition/reporting on the documented HG9 firmware; review privacy/recognition features before disabling. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/hisense-hg9/debloat.txt) |
| Hisense | `com.hisense.storemode` | Retail demonstration mode. Keep only when this TV is used for store demos. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/hisense-hg9/debloat.txt) |
| Hisense | `com.hisense.tv.smartsense` | Optional viewing/usage analytics on documented firmware. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/hisense-hg9/debloat.txt) |
| NVIDIA | `com.nvidia.diagtools` | Vendor diagnostic collector. Keep when troubleshooting with NVIDIA support. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/nvidia-shield-tv-pro-2019/debloat.txt) |
| NVIDIA | `com.nvidia.feedback` | Crash/feedback reporting. Keep if NVIDIA support needs reports. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/nvidia-shield-tv-pro-2019/debloat.txt) |
| NVIDIA | `com.nvidia.nvgamecast` | Legacy GameStream receiver. Keep if your setup still depends on it. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/nvidia-shield-tv-pro-2019/debloat.txt) |
| NVIDIA | `com.nvidia.stats` | Optional device usage statistics. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/nvidia-shield-tv-pro-2019/debloat.txt) |
| Philips | `org.droidtv.amazonalexa` | Optional Alexa bridge. Keep if you use Alexa with the TV. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/philips-75pus8505/debloat.txt) |
| Philips | `org.droidtv.demome` | Retail demonstration loops. Keep for store demonstration use. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/philips-75pus8505/debloat.txt) |
| Philips | `org.droidtv.nettvrecommender` | Optional content-promotion row. Disabling removes Top Picks. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/philips-75pus8505/debloat.txt) |
| Philips | `org.droidtv.sticker` | Retail/demo banners. Optional for a home set. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/philips-75pus8505/debloat.txt) |
| Philips | `org.droidtv.usagelogger` | Optional vendor usage/key-event reporting on the documented TP Vision firmware. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/philips-75pus8505/debloat.txt) |
| Sony | `com.sony.dtv.demosupport` | Retail/demo support. Disabling removes those demonstration features. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/sony-kd-65x80j/debloat.txt) |
| Sony | `com.sony.dtv.multiscreendemo` | Shop demonstration content; unnecessary for ordinary home viewing. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/sony-kd-65x80j/debloat.txt) |
| Sony | `com.sony.dtv.sonybugreportsys` | Vendor bug-report uploader. Keep it when Sony support needs diagnostic reports. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/sony-kd-65x80j/debloat.txt) |
| Sony | `com.sony.dtv.sonytab` | Optional Sony content tab on the stock home screen. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/sony-kd-65x80j/debloat.txt) |
| Sony | `com.sonyericsson.idd.agent` | Optional vendor usage reporting; disabling stops its data collection. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/sony-kd-65x80j/debloat.txt) |
| Sony | `com.sonypicturescore` | Optional streaming service. Keep if you watch or redeem content through it. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/sony-kd-65x80j/debloat.txt) |
| TCL | `com.tcl.MultiScreenInteraction_TV` | Vendor phone control and sharing. Keep if you use TCL phone integration. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-55c715/debloat.txt) |
| TCL | `com.tcl.airplay2` | Apple AirPlay receiver. Disabling stops AirPlay; keep it for iPhone/Mac streaming. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.bi` | TCL usage analytics. Optional: stops vendor usage reporting. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-55c715/debloat.txt) |
| TCL | `com.tcl.browser` | Optional web browser. Disabling removes web browsing provided by this app. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-qm6k-google-tv/debloat.txt) |
| TCL | `com.tcl.channelplus` | TCL internet/FAST channels. Keep enabled if you watch these channels. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.esticker` | Shop/demo stickers. Optional on a home TV; firmware can re-enable retail components. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.exhibit` | Retail demonstration content. Leave enabled if this is a store demonstration set. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.gallery` | Photo viewing/screensaver features. Keep if you use the stock gallery or photo backdrop. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.logkit` | Vendor diagnostic log collection. Keep enabled when TCL support needs device logs. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.magiconnectfree` | T-Cast/MagiConnect integration. Disabling stops this companion/mirroring feature. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.messagebox` | Vendor promotional messages; disabling also removes messages delivered by this app. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.miracast` | Wireless screen mirroring. Keep enabled if you mirror your phone or computer. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.ocean.instructions` | On-screen user manual; disabling removes access to this manual. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.overseasappshow` | Optional vendor app showcase; its recommended-app tiles disappear. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-g03-google-tv/debloat.txt) |
| TCL | `com.tcl.repairguide` | Vendor troubleshooting guide. Keep enabled if you use its support instructions. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.smartalexa` | Amazon Alexa integration. Keep enabled if you use Alexa voice control. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.tv.plus` | Optional TCL content app. Disabling removes its TV+ content, not the core com.tcl.tv input player. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-qm6k-google-tv/debloat.txt) |
| TCL | `com.tcl.tv.tclhome_passive` | Background vendor home-content feed. Disabling removes the content it supplies. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.ui_mediaCenter` | USB media browser. Keep enabled for the stock USB media interface; use another player before disabling. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.usercenter` | TCL account sign-in and related services. Keep for TCL account features; some firmware re-enables it at boot. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| TCL | `com.tcl.waterfall.overseas` | TCL content browser and promotional feed. Disabling removes these recommendations. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/tcl-c755-google-tv/debloat.txt) |
| Xiaomi | `com.mitv.tvhome.mitvplus` | Optional internet/FAST channels. Keep if you watch Mi TV+. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/xiaomi-tv-a-pro-2026/debloat.txt) |
| Xiaomi | `com.mitv.tvhome.oemtab` | Optional Mi Home content tab on Google TV. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/xiaomi-tv-a-pro-2026/debloat.txt) |
| Xiaomi | `com.xiaomi.mimusic2` | Optional music app. Disabling removes this music service. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/xiaomi-tv-a-pro-2026/debloat.txt) |
| Xiaomi | `com.xiaomi.statistic` | Optional vendor usage-statistics reporting. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/xiaomi-tv-a-pro-2026/debloat.txt) |
| Xiaomi | `com.xiaomi.tvqs.overseas.y24` | Quality statistics and log upload. Keep when troubleshooting with vendor support. | [Report](https://github.com/Gathin23/cleanyourtv/blob/9071ec8d2f41826778f6b4c7f5f42dadc22f5b22/examples/xiaomi-tv-a-pro-2026/debloat.txt) |

## Protected packages and reasons

These are blocked in Detox and app-manager disable/clear/uninstall actions. This conservative list includes services that some debloat presets remove: casting, Assistant/accessibility, installation approval, TCL TV guard and TV services, Philips source selection and OEM TV APIs can be required by features users rely on.

| Exact package | Why it remains enabled |
|:--|:--|
| `android` | Android framework and core platform services. |
| `com.android.externalstorage` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.android.keychain` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.android.location.fused` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.android.networkstack` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.android.packageinstaller` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.android.permissioncontroller` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.android.settings` | System settings and configuration. |
| `com.android.shell` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.android.systemui` | System interface, notifications and device controls. |
| `com.android.tv` | Can provide live-TV tuning or input playback. Kept enabled. |
| `com.android.tv.settings` | TV settings and configuration. |
| `com.android.vending` | Play Store installs and updates apps. |
| `com.google.android.apps.mediashell` | Receives Cast sessions from phones and other devices. Keep for casting. |
| `com.google.android.apps.tv.launcherx` | Stock Google TV Home; kept as a recovery home screen. |
| `com.google.android.ext.services` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.google.android.gms` | Play services supports app authentication, Cast, updates and other device integrations. |
| `com.google.android.gsf` | Google account/device framework. |
| `com.google.android.katniss` | Provides voice and home-screen search. Disabling can break the remote microphone and typed home search. |
| `com.google.android.leanbacklauncher` | Stock legacy Android TV Home; kept as a recovery home screen. |
| `com.google.android.marvin.talkback` | Accessibility screen reader. Kept to preserve accessible TV navigation. |
| `com.google.android.networkstack` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.google.android.packageinstaller` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.google.android.permissioncontroller` | Core Android security, device setup, network, installation or system integration. Kept enabled to preserve normal device functions. |
| `com.google.android.tts` | Speech output used by accessibility and voice features. Keep enabled. |
| `com.google.android.tungsten.setupwraith` | Google TV setup/home integration. Disabling is firmware-dependent and not recommended here. |
| `com.google.android.tv` | Can provide live-TV functions; disabling is not recommended without firmware-specific evidence. |
| `com.google.android.tv.remote.service` | Remote service can also provide microphone/hotkey functions on TCL and other TV firmware. |
| `com.google.android.tvlauncher` | Stock Android TV Home; kept as a recovery home screen. |
| `com.mediatek.tv.systemupdater` | Needed for TV firmware, remote or peripheral updates. Keep enabled so updates and recovery remain available. |
| `com.mediatek.wwtv.tvcenter` | Live-TV tuner and HDMI/source interface used by multiple brands. |
| `com.mitv.livetv` | TV tuner and live-input functions. |
| `com.mitv.overseaservice` | Supports remote keys and TV integration on international Xiaomi models. |
| `com.mitv.upgrader` | Needed for TV firmware, remote or peripheral updates. Keep enabled so updates and recovery remain available. |
| `com.sony.dtv.softwareupdate` | Needed for TV firmware, remote or peripheral updates. Keep enabled so updates and recovery remain available. |
| `com.sony.dtv.sonysystemservice` | Core Sony device integration and system services. |
| `com.sony.dtv.tvapi` | Hardware/system interface used by Sony TV components. |
| `com.sony.dtv.tvx` | Core TV input, tuner and playback interface. |
| `com.tcl.UpdatePeripheral` | Needed for TV firmware, remote or peripheral updates. Keep enabled so updates and recovery remain available. |
| `com.tcl.appmarket2` | Some TCL firmware uses this component for APK approvals, app settings or background permissions. Kept to avoid blocked installs or services. |
| `com.tcl.appstatecontroller` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.autopair` | Controls remote keys, Bluetooth pairing or the microphone. Disabling can break remote buttons or voice input. |
| `com.tcl.dashboard` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.factory.view` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.fota` | Needed for TV firmware, remote or peripheral updates. Keep enabled so updates and recovery remain available. |
| `com.tcl.framework.custom` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.guard` | Some TCL firmware uses this component for APK approvals, app settings or background permissions. Kept to avoid blocked installs or services. |
| `com.tcl.initsetup` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.keycustomfunctionservice` | Controls remote keys, Bluetooth pairing or the microphone. Disabling can break remote buttons or voice input. |
| `com.tcl.micmanager` | Controls remote keys, Bluetooth pairing or the microphone. Disabling can break remote buttons or voice input. |
| `com.tcl.partnercustomizer` | Can supply the stock home Inputs shortcut and vendor integration. Kept so source selection is not lost. |
| `com.tcl.rc.ota` | Needed for TV firmware, remote or peripheral updates. Keep enabled so updates and recovery remain available. |
| `com.tcl.settings` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.showmode` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.sourcemanager` | TCL live-TV, HDMI playback or source-selection component. Disabling can remove input playback or the Source menu. |
| `com.tcl.sourcemananger` | TCL live-TV, HDMI playback or source-selection component. Disabling can remove input playback or the Source menu. |
| `com.tcl.suspension` | TCL live-TV, HDMI playback or source-selection component. Disabling can remove input playback or the Source menu. |
| `com.tcl.system.server` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.systemserver` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.tcl_bt_rcu_service` | Controls remote keys, Bluetooth pairing or the microphone. Disabling can break remote buttons or voice input. |
| `com.tcl.tcltvservice` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.ttvs` | Analytics can be bundled with casting, voice or update-related functions here. Its role varies by firmware, so this app does not recommend disabling it. |
| `com.tcl.tv` | TCL live-TV, HDMI playback or source-selection component. Disabling can remove input playback or the Source menu. |
| `com.tcl.tvinput` | TCL live-TV, HDMI playback or source-selection component. Disabling can remove input playback or the Source menu. |
| `com.tcl.tvpassthrough` | TCL live-TV, HDMI playback or source-selection component. Disabling can remove input playback or the Source menu. |
| `com.tcl.tvservice` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.tcl.tvweishi` | Some TCL firmware uses this component for APK approvals, app settings or background permissions. Kept to avoid blocked installs or services. |
| `com.tcl.versionUpdateApp` | Needed for TV firmware, remote or peripheral updates. Keep enabled so updates and recovery remain available. |
| `com.tcl.xian.StartandroidService` | TCL system/settings integration; firmware can depend on it for device setup, menus or hardware control. Kept enabled. |
| `com.xiaomi.mitv.updateservice` | Needed for TV firmware, remote or peripheral updates. Keep enabled so updates and recovery remain available. |
| `io.konnecttv.glowlauncher` | Active home-screen companion for Konnect TV Plus. Use Boost to remove it safely. |
| `io.konnecttv.plus` | Companion app; keep enabled while managing the TV. |
| `mitv.service` | Handles TV/remote functions, including keys, on documented Xiaomi sets. |
| `org.droidtv.ambihuecommon` | Shared Ambilight/Hue integration used by persistent TV services. |
| `org.droidtv.eum` | Shared persistent TV integration; demo apps are separate packages. |
| `org.droidtv.fastip` | Some Philips Sources drawers depend on this package even though it also supplies FAST channels. Kept enabled. |
| `org.droidtv.playtv` | Philips live-TV playback interface. |
| `org.droidtv.settings` | TV settings and configuration interface. |

Protected name patterns: `inputmethod`, `.ime`, `keyboard`, `bluetooth`, `tvinput`, `hdmi`, `providers`, `webview`, `permissioncontroller`, `packageinstaller`, `networkstack`, `braviasync`, `tvapi`, `systemupdater`.

## Already off and restoration

Inventory reads the TV owner profile (user 0), including disabled apps and system APKs uninstalled for that user. It does not rely on Plus history, so changes made before installing Plus appear as **Already off**.

- **Enable** runs `pm enable --user 0`, refreshes inventory and verifies the app is enabled.
- **Restore original app** first runs `cmd package install-existing --user 0`, then enables and verifies it. This works only while the system APK is still available on the TV.
- An app actually deleted from the device cannot be recreated without its APK. Work/profile users other than owner are outside this inventory.
- Factory-disabled services are included. Their presence is not a recommendation to activate them; restore a component only when you need its function.
- Successful Plus disable actions are recorded in Detox history. A failed command keeps its history entry and exposes the TV's response. Restoring an app disabled outside Plus does not need an old history entry.

Debugging permissions and manufacturer package-manager restrictions still apply. Collect diagnostics to preserve a failed command's exit code and response.

## Attribution

CleanYourTV: Copyright 2026 Gathin, MIT; [license notice](licenses/CleanYourTV-MIT.txt). Original common Android TV rules: [KonnectTV](https://github.com/yashoncode/konnect-tv), MIT. Sources describe the exact observed packages; Plus uses its own conservative classification and explanatory text.
