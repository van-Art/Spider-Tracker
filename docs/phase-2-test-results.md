# Phase 2 — Test results

Final validation: **23 October 2026, Asia/Yerevan** (22 October UTC). Debug APK built, installed and exercised on Android 17 / API 37. **6 JVM tests and 8 device tests passed; no failures or skipped tests. Lint: 0 errors, 8 warnings.**

## Build and artifact

| Check | Result / evidence |
|---|---|
| `:app:assembleDebug` | PASS — final [build log](evidence/phase2-final-build-tests.log), `BUILD SUCCESSFUL` |
| `:app:testDebugUnitTest` | PASS — [6-test JUnit XML](evidence/unit-tests.xml) |
| `:app:connectedDebugAndroidTest` | PASS — [8-test device XML](evidence/device-tests.xml) |
| `:app:lintDebug` | PASS — [lint report](evidence/lint-results.txt), zero errors |
| Install and launch | PASS — final APK installed on the task AVD; actual app screenshots and device metadata retained |
| Android Back (manual) | PASS — hardware Back from the menu returns to map; [UI hierarchy](evidence/back-after-menu.xml) reports “Open navigation” and no longer “Close navigation” |
| APK signature | PASS — debug certificate, APK Signature Scheme v2 verified; [signer report](evidence/apk-signing.txt) |
| Package boundary | PASS — delivery identical to Gradle output, notices/fonts included, reference archive absent, no location permissions; [delivery record](evidence/delivery.json) |

APK: [dist/spider-tracker-phase2-debug.apk](../dist/spider-tracker-phase2-debug.apk), **60,429,313 bytes** (~57.6MiB), universal native architectures `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`. Gradle's original output is `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256:

```text
26e0f79fcc5963c95cb737b641cea7d58500e69aeb9b7579660ab3a45c611aa1
```

Manifest evidence: [aapt2 badging](evidence/apk-badging.txt). Min API 26, target API 36, compile API 37. Packaged permissions are Internet, network state, Wi-Fi state and AndroidX's application-scoped receiver permission. MapLibre's transitive coarse/fine location declarations are explicitly removed by the manifest merger. No location permission prompt, GPS implementation or route/backend dependency is present.

## Tested behavior

The six JVM tests cover shared marker/log filtering and event/trailer retention; detail/media/Back clearing; fixture IDs, geographic validity and date sorting; superseding camera requests; saved filter/sound/section/startup state; measured phone/desktop/landscape geometry; once-per-section explanations and restartable toast state.

The eight device tests cover:

1. Confirmed/rumored toggles update map semantics and Activity Log state.
2. Phone menu opens vertical Web Watch and closes it; desktop arrows are absent in that phone layout.
3. Rumored detail opens media and returns to an empty message state after closing.
4. Radar target/global actions issue the expected demo/global camera requests.
5. Help dismisses on tap; video strip expands and selects the second original demo clip.
6. Filter state survives activity recreation.
7. Explicit sound choice → tutorial tap → loading → usable live map.
8. The real native MapLibre style/layers/source load and an Android-injected pan changes native camera longitude.

Device configuration: existing `Medium_Phone_API_37.0` AVD, x86_64 Google Play Android 17, emulator 37.2.12, SwiftShader, headless. Initial test serial was `emulator-5554`; final captures use `emulator-5556` after port reassignment. Interaction tests run in portrait with physical 780×1688, density320 (logical390×844), font scale1.0. Tablet and short-landscape layouts are captured and visually reviewed separately; the phone-specific test assertions must not be run against a leftover landscape viewport.

Final visual evidence covers 26 states: 16 phone, 5 tablet and 5 short-landscape captures. Thirteen have selected same-size Phase 1 pairs. See [visual comparison](visual-comparison.md) for dimensions, masks, hashes, original artwork substitutions and remaining differences. Screenshot fixtures bypass startup timers/section explainers and freeze decorative time at0.7s; the actual startup is tested independently.

## Reproduce

With `local.properties` pointing to the installed SDK and a supported JDK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME=Join-Path (Get-Location) '.gradle-user-home'
# Verify the AVD identity/serial with adb devices and adb -s SERIAL emu avd name.
# Run with only the intended test emulator connected.
$deviceSerial='emulator-5556'
adb -s $deviceSerial shell wm size 780x1688
adb -s $deviceSerial shell wm density 320
adb -s $deviceSerial shell settings put system font_scale 1.0
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

For packaging checks, refresh badging/signing/report copies as described by their evidence filenames, then run `python scripts/verify-package.py`. For visual evidence use `scripts/capture-android.ps1 -Serial emulator-5556` and `python scripts/compare-android.py`; the capture script validates the task AVD's identity and uses this host's SDK path.

## Resolved issues and remaining checks

Initial failures exposed two incompatible platform theme attributes at min API26, an older transitive Espresso version reflecting a removed `InputManager.getInstance`, a Compose-only swipe that did not reach the embedded native view, and phone-specific assertions accidentally run after landscape captures. Theme attributes were removed, Espresso3.7.0 was pinned, native-view input injection was used, and the phone viewport was restored before the final successful suite. The [official Espresso release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso_370) describe the input-service reflection fix.

During screenshot collection the headless emulator session stopped and ADB ports were reused. The script was updated to check AVD identity and retain per-image device/API/density/font metadata; the final set was recaptured on the verified task AVD. This interruption is not presented as a passing durability test or an established application-crash diagnosis.

Lint warnings are: target API36 while37 is installed; newer Gradle, activity, two lifecycle and MapLibre versions available; Android12+ backup-rules recommendation; optional `createBitmap` KTX suggestion. They are non-blocking and not represented as zero warnings. The app disables backup and uses only independent fixtures; production backup/SDK policy should be reviewed before adding real content/location.

No physical-device/GPU matrix, Android26 runtime, TalkBack audit, real audio quality, exhaustive font-scale/locale sweep, frame-time profiler or cold-network-loss certification was performed. Native world geometry is bundled, but a screenshot from a renderer cache is not proof of complete offline street/label coverage. The debug build is not a production release. GPS, routing, real media playback and backend services were deliberately not started.
