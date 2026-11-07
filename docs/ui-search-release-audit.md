# Search, motion, and release audit

This change targets search reliability, long-running decorative animations, and reproducible local
release signing. It does not establish that every app feature is bug-free or that frame rates have
improved on physical devices.

## Changes

- Search failures now replace the indefinite loading indicator with a Retry action. Pagination
  failures keep existing results and provide a separate retry. Empty summary results show an empty
  state rather than a blank screen.
- The most-played-songs-by-artist Room query now uses `@Transaction`, keeping the song and its
  relations consistent while history/library data changes.
- Switching search filters cancels the previous initial request. Pagination claims its per-filter
  request slot before starting a coroutine, releases it in `finally`, deduplicates appended items,
  and ends pagination if the server repeats its continuation token.
- Online suggestions wait 250 ms inside `flatMapLatest`; each new input cancels the preceding wait
  or request immediately. Clearing the query and offline library search remain immediate.
- The shared motion policy stops its consumers while the activity is below STARTED. Search dots,
  the video loading clock, and legacy shimmer now respect app/system reduced motion. Visuals and
  static loading indicators remain available.
- Decoder and encoder capability probes handle a missing `videoCapabilities` result explicitly.
  The decoder preserves its existing failure policy for ordinary versus high-resolution video.
- Bluetooth device names come from the connected output devices instead of deprecated routing
  flags; BLE headset/speaker detection is guarded by Android 12 availability.
- Changelog links use accessible `LinkAnnotation` handling. URL ranges are computed after trimming,
  matching the rendered text and avoiding incorrect/out-of-bounds spans.
- Hilt imports, clipboard access, progress indicators and mirrored volume icons use their current
  APIs. Compiler-proven redundant null operators and unreachable branches were removed.
- Playback coroutine/Media3 opt-ins now annotate MusicService rather than StreamFailure. The
  Android 7 navigation-bar scrim retains a documented, locally scoped deprecated API call because
  those devices cannot draw dark navigation icons. The existing legacy call-state fallback remains
  limited to Android 7–11; newer Android uses AudioManager's mode listener.
- The release keystore can live outside the repository through `RELEASE_STORE_FILE` and
  `RELEASE_STORE_TYPE`. Release signing explicitly disables v1/v3 and enables v2/v4. v4 needs its
  matching `.apk.idsig` companion file. No passwords or keys are stored in source.
- The shell helper handles both GNU and macOS sed and copies v4 companion files alongside APKs.
- Plugin resolution includes the official Google/Maven/plugin repositories. Android library
  modules use the current typed DSL accessor instead of its deprecated predecessor.

## Reproduce

Use JDK 21, SDK 36, NDK 27.0.12077973, CMake 3.22.1, and an Android SDK path in
`local.properties`. GitHub Actions restores the repository's existing `MOCHY_RELEASE_KEYSTORE_BASE64`,
`MOCHY_RELEASE_STORE_PASSWORD`, `MOCHY_RELEASE_KEY_ALIAS` and `MOCHY_RELEASE_KEY_PASSWORD`
secrets to the default PKCS12 keystore path. The earlier uploaded JKS is not used.

```sh
./gradlew :app:testUniversalGmsDebugUnitTest -PbuildAbis=universal \
  --no-configuration-cache --console=plain --warning-mode=all --max-workers=1 --no-parallel
./gradlew :app:assembleUniversalGmsRelease :app:lintUniversalGmsRelease \
  -PcompressNativeLibs -PbuildAbis=universal --no-configuration-cache \
  --console=plain --warning-mode=all --max-workers=1 --no-parallel
```

Run the two Gradle invocations sequentially. A combined debug/release invocation failed inside
KSP's FIR annotation resolution while both variants were being processed.

GMS preserves the fork's Cast feature. The existing `build.sh --release` helper defaults to FOSS;
its APK does not include Cast.

## Verification limits

The project currently sets `abortOnError = false` and `warningsAsErrors = false` for Android lint.
A successful Gradle exit alone therefore does not prove zero lint errors or warnings. Check the
lint XML/HTML report and full compiler output before making that claim.

Protobuf was upgraded from 0.9.6 to 0.10.1, which supports AGP 9's public DSL; the legacy
`android.newDsl=false` compatibility option was removed. Library scripts use the typed DSL.
`-PbuildAbis=universal` excludes unused ABI variants before configuration, avoiding work and
Google Play warnings from separate 32-bit distribution flavors. Without that property the full
existing flavor matrix remains available. `build.sh` supplies the property for its selected ABIs.

No emulator with KVM or physical Android device is attached to this environment. Before accepting
visual smoothness, review the code again and test on a device: type/delete rapidly, switch search
filters during a request, interrupt/retry network access, scroll through multiple result pages,
background/foreground while loading, and toggle both app and Android animation settings. Compare
frame timing and visual captures on the same device/build type; static inspection is not a frame
benchmark.

## Signing policy verification

Both release workflows use the repository's existing `MOCHY_RELEASE_*` secrets and trusted
`.github/release-certificate.sha256` fingerprint. They require v2/v4, reject v1/v3, and retain the
matching `.apk.idsig` sidecar. No uploaded JKS or new signing bundle secret is used.

`test_signature_schemes.py` constructs a small real APK using Android build tools, signs it with a
temporary fixture key, and exercises the verifier. It accepts v2/v4-only APKs and rejects v1, v3,
a wrong certificate, and a corrupted v4 signature. These fixture tests validate the verification
policy; they do not establish that the application APK has been built or verified. The actual
repository release key is available only to the repository's GitHub Actions signing job.

## GitHub Actions evidence and subsequent fixes

Commit `c57b4f4` built an actual universal GMS release APK in run
[42320069265](https://github.com/XbibzOfficial777/Mochy-Music/actions/runs/42320069265).
The original repository certificate and v2/v4-only signatures passed verification; APK metadata,
ZIP integrity, native ELF checks, and 16 KB ZIP alignment also passed. Unit tests passed and the
compiler/resource warning checker reported zero. However, the strict lint gate FAILED with
112 errors and 1588 warnings. This run is not a warning-free release.

The independent Android CI run
[42320069278](https://github.com/XbibzOfficial777/Mochy-Music/actions/runs/42320069278)
passed, including emulator launch checks on API 30 and 35. These are launch checks, not search,
playback, or frame-time benchmarks.

Follow-up source fixes remove the nonexistent CrashActivity manifest entry, guard newer Android
APIs, use observable resources/locales, remember initial flow snapshots, and remember the parent
navigation entry. NewAction now accepts an ordinary click callback: previously it invoked a
composable callback during composition, allowing side effects to repeat on recomposition.
Tab ownership reads the public ComposeNavigator back stack rather than restricted NavController
internals; this app's tab destinations use the Compose navigator. French/Portuguese plural categories
and Persian quantity/seek placeholders are repaired. UTF-8 BOM characters inside CSV string literals
are expressed as explicit escapes. The CI debuggable-APK guard now exits explicitly on a match.

Lint remains strict: existing findings are not baselined or globally suppressed. Check the latest
run before claiming these follow-up fixes pass release validation or that all lint debt is gone.

The first public-navigator migration in `1f697069` failed emulator cold-start checks on API 30
and 35: ComposeNavigator state was read before NavHost attached it. The follow-up reads its stack
only after `currentBackStackEntryFlow` emits a destination. This regression was caught by device
execution despite passing all 170 JVM tests and Kotlin compilation. The failing commit's APK must
not be treated as validated. Verify the follow-up emulator run before accepting the fix.

The follow-up local lint report reduced errors from 112 to 1 (RestrictedApi for the old WebView
header override), and warnings from 1588 to 1580. AndroidX WebSettingsCompat 1.12.1 documents
X-Requested-With as deprecated in late 2022 and discontinued; its setter restores legacy header
behavior. The obsolete empty-allow-list override and its restricted feature gate are removed,
leaving WebView's default header policy. Authentication behavior on old WebView providers still
needs manual coverage. Settings search keys cached results by a locale string rather than a mutable
Resources.Configuration object. Build workflows use Node 24 versions of artifact, Gradle, and SDK
actions to address the observed Node 20 deprecation annotations. These changes require a fresh run.
