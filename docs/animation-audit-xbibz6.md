# Animation audit: xbibz.6

Scope: player backdrops and sheet, alternate poster style, navigation, loading skeletons, the
playing equalizer and song-carousel settling. This is a targeted audit, not a claim that every
animation in the application has been rewritten or that a particular device can never jank.

- Navigation slides and fades now use coordinated, bounded durations; the sheet uses a slightly
  quicker, near-critical spring. Direct drag tracking is unchanged. App-level motion-off snaps the
  audited programmatic sheet movements and hand-stepped carousel while keeping gestures usable.
- Apple Music preview backgrounds respect their `animate` flag. Live poster backgrounds stop when
  the player is hidden or fully covered; the flagship also stops its colour drift when the colour
  backdrop is effectively invisible. Static/disabled previews do not create an infinite clock,
  and preview drawing is clipped to its own bounds.
- The six Apple blobs use cached, origin-centred radial brushes translated at draw time, rather
  than constructing shaders for each drift frame. Palette animation state is read during drawing,
  not each-frame composition. Orbits use complete revolutions over a 72-second cycle, so the loop
  boundary is continuous. Cancellation is propagated and palette state is written on the UI thread.
- Cover-gradient warp clocks do not run for other background types, unsupported shaders, or when
  the app/system motion settings are off. Equalizer loops cancel promptly when obscured and show a
  static playing indicator under reduced motion. Skeleton waves stop while obscured or reduced
  motion is requested. Loading reveals respect the motion switch.
- Android's animator duration scale is applied to the manually stepped carousel. System scale zero
  and the app Animation switch are tested for the audited decorative components, not asserted for
  every unrelated animation in this large app.

The debug-only fixture compares cached and original rendering at identical colours and phase, and
checks animated/static previews and app/system reduced motion via emulator screenshots. JVM tests
cover orbit continuity and the update-policy matrix. Release verification checks non-debuggable
builds, the trusted signing certificate, metadata, SHA-256, compressed ELF class/machine, exact
ABI sets and matching native library names. Emulator results do not establish physical-device frame
rates, battery use, thermal behaviour, graphics-driver compatibility or smoothness in every scene.
