# Project status — Neon Player 1.1

## Implemented in source
- MediaStore video scan, search, folder filtering, thumbnails and metadata.
- Persistent favorites, playback progress, playlists and gesture preferences via SharedPreferences.
- Media3 MediaSessionService + MediaController for system media session and background playback.
- Embedded audio/text track selection and external subtitle picker.
- Picture-in-Picture manifest support and automatic entry while playing when the user leaves the activity.
- Direct HTTP/HTTPS, HLS, DASH and RTSP media sources through Media3 modules.
- Double-tap seeking, long-press speed, horizontal seek drag, vertical brightness/volume gestures.
- GitHub Actions workflow to build and upload a debug APK artifact.
- Playlist playback queue and automatic next-item behavior when autoplay is enabled.

## Not yet verified
- No local Android SDK/Gradle build was available in the generation environment; source has not been compiled here.
- No physical-device test has been run on Motorola Edge 2024 / Android 15.
- Network playback depends on URL reachability, server behavior, codec/container support and Media3 protocol support.
- External subtitle formats beyond common SRT/MIME handling need device testing.
- Full SMB/NFS/SFTP/DLNA browsing is not implemented.
- No release signing configuration or Play Store bundle is included; CI creates a debug APK.
