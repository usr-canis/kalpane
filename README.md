# ಕಲ್ಪನೆ · Kalpane

A notes app for Android that combines Markdown text with stylus drawing.

Status: rewrite in progress. The previous version is kept on the `pre-rewrite` branch.

## How notes are stored

Each notebook is a folder and each note is a plain Markdown file:

```
<notebook>/<note title>.md
```

The same layout is used in your private notes repository, so notes stay readable on GitHub.

## Build

JDK 17 and Android SDK 36:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

CI builds the debug APK on every push to `main` and uploads it as a workflow artifact.

## License

GPL-3.0-or-later for the application. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for the Mallige font and supplied artwork attribution.
