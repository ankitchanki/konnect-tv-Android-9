# Phone permission cleanup in 2.3.1

The pre-fix manifest-merger-debug-report.txt identified these **implied** permissions:

- WRITE_EXTERNAL_STORAGE: `io.github.muntashirakon.adb has a targetSdkVersion < 4`.
- READ_PHONE_STATE: `io.github.muntashirakon.adb has a targetSdkVersion < 4`.
- READ_EXTERNAL_STORAGE: `io.github.muntashirakon.adb requested WRITE_EXTERNAL_STORAGE`.

The phone manifest now uses explicit tools:node="remove" entries for all three. APK badging is checked before publication; none may remain. Document picking uses Android's Storage Access Framework and app-private files. No phone-state permission is needed for ADB or TV control. The app retains VIBRATE for the requested feedback and network/multicast permissions for authorized TV connections/discovery.
