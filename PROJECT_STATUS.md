# Neon Player 1.2 — status and verification

## Included in this source update
- Folder cards now show a 2×2 mosaic of real video-frame thumbnails obtained from Android `ContentResolver.loadThumbnail` on Android 10+.
- Folder browser supports list cards, compact tree-style rows, two-column folder cards, and sorting by name, video count, or total size.
- Folder cards display video count, aggregate size, latest modification date, and a quick-open action.
- Long-press playback speed preference now spans 0.20×–4.00×; double-tap seek, horizontal seeking, vertical brightness/volume gestures, audio/subtitle track selection, external subtitle picker, playlists, resume, background session, and PiP remain in the source.
- Existing Neon Player identity and Compose UI are retained; this is not a copy of mpvRx's UI or source tree.

## Important engine status
- **Playback engine is still AndroidX Media3 in this ZIP. `libmpv` is NOT yet integrated.**
- mpvRx 3.0.2 uses its own mpvlib Android native build and player integration. The upstream `mpv-android` project explicitly notes that it is an application rather than a drop-in AAR library. A real migration requires integrating the native AAR/source, surface lifecycle, mpv event/property bridge, track controls, seek/resume, and service lifecycle; adding a dependency line alone would not make Neon Player use libmpv.
- Do not describe this package as an mpv-powered build until that integration is implemented and CI/device playback tests pass.

## Not implemented in this update
- Full mpvRx-equivalent tree traversal across physical storage providers and network shares.
- SMB/NFS/SFTP/DLNA browsing.
- Full theme editor, wallpaper backgrounds, AMOLED-wide color adaptation, and launcher icon builder/dynamic icon variants.
- Native libmpv playback, mpv post-processing, and mpv-specific subtitle/rendering options.

## Verification
- ZIP structure and source edits are prepared, but no Android SDK/Gradle build or physical-device test was run in this environment.
- The CI workflow installs Gradle 8.13 directly and runs `gradle :app:assembleDebug`; this avoids relying on a wrapper JAR that was absent from the previous archive.
- Treat any feature not listed under “Included” as not implemented or not tested.
