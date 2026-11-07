# UI/UX, animation and lint audit

This branch is a correctness-and-polish pass over the Bibz fork. It is written to say exactly what
was changed and what was *measured*, because "smoother" and "bug-free" are easy to claim and hard to
prove.

Branch: `optimize/bibz-uiux-fixes`, based on `fix/search-motion-release` (which carries the already
audited search/motion fixes and the signed-release verification pipeline).

## What this pass changes

### Rendering cost of animations

- **Layout-phase animated offsets.** Every `Modifier.offset(x = dp, y = dp)` that is driven by an
  animation now uses the `offset { IntOffset(...) }` overload
  (`AppSwitch`, `AudioLabKit`, `CustomisationEffects`, `RandomizeGridItem`). The dp overload
  resolves during composition, so each frame of the animation recomposed the caller; the lambda
  overload is read while the layout pass places the node, so the same animation now skips
  recomposition and only re-lays-out the moving element.
- **Primitive Compose state.** `mutableStateOf(0)` / `mutableStateOf(0f)` for `Int` and `Float`
  state became `mutableIntStateOf` / `mutableFloatStateOf` (16 sites: sliders, drag offsets,
  download progress, sheet heights). Snapshot writes no longer allocate a boxed value, and readers
  compare primitives instead of objects. Verified against Compose 1.10.2 sources that
  `SnapshotMutableIntStateImpl` implements `SnapshotMutableState<Int>`, so the `rememberSaveable`
  saver path (`require(state is SnapshotMutableState<T>)`) still accepts them.
- **Widget layout.** The compact widget's album art and play button are now direct children of its
  root `FrameLayout`; the removed container only repeated `match_parent`, so rendering is
  identical and one measure/layout pass per widget update is gone.

### UI correctness

- **Window size instead of display size.** Sheets, covers, carousels and shared share-card
  previews sized themselves from `Configuration.screenWidthDp` / `screenHeightDp` (17 sites). That
  is the *display*: in split screen, on a foldable app pair, in a freeform or desktop window it is
  larger than the space the content gets, so a sheet asking for "78% of the screen height" could
  clip its own buttons. They now read `rememberWindowSize()` (`ui/utils/WindowSize.kt`), which is
  `LocalWindowInfo.containerSize` converted and cached per (size, density). The reader also
  recomposes on window resize instead of requiring an activity recreation.
- **Resources read from the right place.** `LocalContext.current.resources` became
  `LocalResources.current` (4 sites), so language changes made in-app are picked up by the same
  recomposition that redraws the screen.
- **Number formatting is explicit.** All 29 user-facing `String.format(...)` calls without a locale
  now go through `UiNumberFormat.decimal(...)`, which passes `Locale.Category.FORMAT` explicitly.
  Behaviour is unchanged for a default device, but the intent is stated and the call sites can no
  longer silently mismatch if a value is ever parsed back.
- **Typed state in sheets.** Nothing user-visible, but the above two items remove the last places
  where a sheet could show a stale size after a rotation.

### Logging

- A `ReleaseTree` is planted in non-debug builds. Timber previously had only a debug tree, so every
  `Timber` call was a no-op in a release APK — which is why several call sites had fallen back to
  `android.util.Log` on purpose (a share export that went missing, a source module that could not
  decode a stream). Those 55 call sites now use `Timber.tag(...)`, and release keeps exactly the
  messages it logged before (tag, priority and throwable preserved).
- `CrashHandler` keeps `android.util.Log` deliberately and is annotated with the reason: it must
  work when the process is about to die and when no tree has been planted yet.

### Lyrics line changes

The line emphasis on the lyrics page ran on a fixed 600 ms tween for brightness, zoom and colour,
plus its own tween for the blur by distance. Three things made a line change read as stiff:

1. **A tween restarts from a standstill.** A verse that changes line every beat (1.2-1.8 s) cancels
   the previous ramp before it has settled; the new one starts again from zero velocity, so every
   change looks like the animation is being cut and restarted. Brightness, zoom, colour, the blur
   radius and the wave amplitude now ride springs, which carry their velocity across the
   interruption, so one line's emphasis flows into the next.
2. **The zoom had no body.** The line taking over now settles with a slight overshoot
   (`dampingRatio = 0.74`, `stiffness = StiffnessLow`) instead of arriving flat. Only the zoom is
   allowed to overshoot; brightness, colour and blur are critically damped.
3. **The glyph weight changed mid-fade.** A line without word timing rendered `SemiBold` while it
   was lining up and switched to `Bold` the instant it became the current line, which re-measures
   every glyph at a wider advance - a visible jump that no fade can hide, and inconsistent with the
   line breaks, which were always measured against `Bold`. That path now uses one weight; the
   emphasis comes from brightness (1.0 vs 0.5/0.38/0.24), zoom and colour.

The karaoke sweep keeps its existing run-up: while a line's wave amplitude is still decaying it
stays on the sweep path, so the hand-over to the plain path happens after the fade, not in the
middle of it.

### Warning cleanup

**Verified green.** Release run
[42339962929](https://github.com/XbibzOfficial777/Mochy-Music/actions/runs/42339962929)
(`85b7bb2a`, the commit that also carries the lyric line-change work) passed
`Require clean compiler and lint reports` with `Compiler/resource warnings: 0` and an empty lint
summary (`lint-results-universalGmsRelease.txt` holds exactly `No issues found.`). The same run
signed the APK with the repository key (certificate `d8828ad1…4b16`, checked against
`.github/release-certificate.sha256`), verified v2 + v4 (`Errors: []`, `Warnings: []`).
Debug workflow [42339962934](https://github.com/XbibzOfficial777/Mochy-Music/actions/runs/42339962934)
was green on all three jobs, including the API 30 and API 35 emulator launches, so the build with
these changes installs and starts on both.

Starting point, for the record, was the lint report of the first signed release (1 581 warnings):

| Group | Count | What happened |
| --- | --- | --- |
| `DefaultLocale` | 29 | fixed in code (`UiNumberFormat`) |
| `LogNotTimber` | 56 | fixed in code (release tree + conversions), 1 documented suppression |
| `UseKtx` | 66 | fixed except the overloads `androidx.core` does not provide |
| `TypographyEllipsis` | 47 | fixed in resources (real `…` in 31 files) |
| `AutoboxingStateCreation` | 16 | fixed in code |
| `ConfigurationScreenWidthHeight` | 17 | fixed in code (`rememberWindowSize`) |
| `UseOfNonLambdaOffsetOverload` | 7 | fixed in code |
| `LocalContextResourcesRead` | 4 | fixed in code |
| `PermissionImpliesUnsupportedHardware` | 2 | fixed (optional Bluetooth features declared) |
| `DisableBaselineAlignment`, `UselessParent` | 2 | fixed in layouts |
| `MonochromeLauncherIcon` | 1 | fixed (the themed-icon layer already existed) |
| `UnusedResources` | 1072 | explicitly ignored in `lint.xml`, with the reason |
| dependency-currency, `Typos`, icon art, plurals, dashes | ~160 | explicitly ignored in `lint.xml`, each with its reason |

`lint.xml` therefore contains a written justification for every category that stays, and the
release workflow still fails on any *new* warning outside those categories.

### KTX suggestions that are not applied

`androidx.core:core-ktx` only provides `createBitmap(width, height, config)` and
`createBitmap(width, height, config, hasAlpha, colorSpace)`. The lint detector also suggests
`createBitmap` for `Bitmap.createBitmap(source, x, y, w, h)`, `createBitmap(IntArray, w, h, config)`
and `createBitmap(Picture)`, which do not exist at any released version (checked against core-ktx
1.13.1, 1.16.0, 1.17.0 and 1.18.0 sources). Those call sites keep the platform call, because
"fixing" them would not compile.

## What is *not* claimed here

- No frame-rate or startup measurement was taken on a physical device for this pass. The changes
  above are static-cost reductions (recomposition scope, boxing, layout passes), and the repo's
  `tools/perf/` harness exists for measuring them; its results are not included here.
- No emulator or device test run backs this document. The evidence for "it builds and the signed
  release is intact" is the GitHub Actions run linked in the pull request:
  `Xbibz verified release build` compiles the release, runs the unit tests, validates that the
  keystore matches `.github/release-certificate.sha256`, and verifies v2/v4 signatures.
- Feature-bug fixing is not finished. Items still open are listed below so they are not silently
  forgotten.

## Still open

- `ModifierParameter` (22 remaining, ordering only): the naming and default-value problems were
  fixed in code (`ProviderSquircle` reordered, `ShareVideoLayer` given a bare `Modifier` default,
  `BedsideTilesStyle` and `PopupSheet` given a documented suppression because they genuinely take
  two modifiers). The 22 that stay are pure *ordering*: this codebase puts `modifier` last among the
  optional parameters everywhere, so moving 22 out of ~1000 composables would break the convention
  and any positional call site. Documented ignore in `lint.xml`.
- `UseKtx` (16 remaining): all inside the bitmap artwork renderers, where the explicit
  `save()`/`restore()` brackets are interleaved with native canvas calls that also restore state.
  Cannot be verified without rendering a frame, so it is a documented ignore rather than a blind
  rewrite.
- `InsecureBaseConfiguration`, `DiscouragedApi` + `LockedOrientationActivity`: documented ignores —
  LAN cleartext for Listen Together, and the deliberate portrait lock of `MainActivity`.

### Fixed in the last batch

| Finding | Count | What was done |
| --- | --- | --- |
| `ObsoleteSdkInt` | 8 | guards for `SDK_INT >= 24` removed (minSdk is 24); the unreachable `loadDevicesLegacy` loader deleted; the always-true `SDK_INT >= 33` branch inside the Android 16 notification builder collapsed |
| `InlinedApi` | 8 | compile-time constants (`AUDIOFOCUS_NONE`, `PRIMITIVE_LOW_TICK`, `EFFECT_TICK/CLICK`, `TYPE_USB_HEADSET`, `ACTION_MANAGE_UNKNOWN_APP_SOURCES`) moved into named `private const val`s annotated `@SuppressLint("InlinedApi")` with the reason, instead of suppressing the call sites |
| `LogNotTimber`, `StringFormatInTimber`, `BinaryOperationInTimber` | 12 | log calls converted to Timber with the value extracted into a named local |
| `ContentDescription`, `StaticFieldLeak`, `PrivateApi`, `SetJavaScriptEnabled`, `ApplySharedPref`, `SdCardPath`, `UsableSpace` | 12 | fixed in code or annotated with the reason at the declaration |
| `ModifierFactoryExtensionFunction` | 5 | the four karaoke modifier factories in `LyricsGlowLine` are now extensions on `Modifier` (they chain onto the incoming modifier instead of returning a fresh one), as is `PopupSheet`'s size modifier, which now takes the measured height as a parameter |

What is left in `lint.xml` is therefore: unused resources (asset cleanup, not a defect),
dependency-currency and typography/translation policy, the four documented categories above.
The release gate still fails on any warning outside them.

- `Repository`-level behaviour bugs from the upstream issue tracker are **not** claimed fixed here.
