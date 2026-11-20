# bitTube

bitTube. For bitDroid.

A video app for bitDroid (Pixel 10, Android 17) in the Bloom / Material You style: tonal colors,
scalloped cookie shapes, squishy springs, and the floating tab bar from bitMessages.
Kotlin + Jetpack Compose. No API key: YouTube data comes through
[NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), playback is Media3 ExoPlayer.

## Features

- **Sign in** with your YouTube account (Google's own sign-in page, like AlexLMP): your real For you page, subscriptions feed, likes and dislikes, subscribing, Watch later, playlists and history, synced both ways
- **Home**: For you (new uploads from your subscriptions mixed with videos related to what you watched), YouTube's charts, and topic chips
- **Shorts** live in their own app, [bitClips](https://github.com/cashhwocky/bitClips); setup and Settings link to it
- **Feed**: everything new from your subscriptions, newest first, with a creator strip to filter by channel
- **Library**: liked videos, Watch later, history with resume points, your own playlists
- **Search** from the clover button, with suggestions and recent searches
- **Player**: morphing play/pause, Material You squiggly progress bar, double-tap to skip, swipe down to minimize, swipe up for full screen, floating mini player
- **Quality**: pick any resolution per video, plus separate defaults for Wi-Fi and mobile data; playback speed
- **Likes, dislikes** (counts from Return YouTube Dislike), **subscribe**, **add to playlist**, share
- **Themes**: Lilac (default), Material You (wallpaper colors), Mint, Peach, Ocean, Rose, Midnight
- **App icons**: Lilac (default), White, Cookie, Mint, Peach, Ocean, Night
- **In-app updates**: when a newer release is on GitHub, a full-screen update page appears every time the app opens
- Opens youtube.com and youtu.be links

Signed out, subscriptions, likes, playlists and history are stored on the phone only. Signed in, they also go to your YouTube account.

## Install

Grab `bitTube-x.y.z-release.apk` from [Releases](../../releases) and install it.

## Build

CI builds every push; a tag like `v0.1.0` publishes a release with `bitTube-0.1.0-release.apk` and `update.json`.
The tag must match `versionName` in `app/build.gradle.kts`, and bump `versionCode` every release so the updater sees it.

Release signing uses these repo secrets (otherwise the APK is debug-signed and in-app updates
won't install over it): `BITTUBE_KEYSTORE_B64`, `BITTUBE_KEYSTORE_PASSWORD`, `BITTUBE_KEY_ALIAS`, `BITTUBE_KEY_PASSWORD`.

## License

GPL-3.0, because NewPipe Extractor is GPL-3.0.

Made by Alex Desrosier.
