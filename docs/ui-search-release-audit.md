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
  `RELEASE_STORE_TYPE`. Explicit signing enables APK Signature Scheme v2, v3 and v4. v4 needs its
  matching `.apk.idsig` companion file. No passwords or keys are stored in source.
- The shell helper handles both GNU and macOS sed and copies v4 companion files alongside APKs.
- Plugin resolution includes the official Google/Maven/plugin repositories. Android library
  modules use the current typed DSL accessor instead of its deprecated predecessor.

## Reproduce

Use JDK 21, SDK 36, NDK 27.0.12077973, CMake 3.22.1, and an Android SDK path in
`local.properties`. Set `RELEASE_STORE_FILE`, `RELEASE_STORE_TYPE`, `STORE_PASSWORD`, `KEY_ALIAS`,
and `KEY_PASSWORD` in the process environment without committing them.

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
