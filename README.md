# ಕಲ್ಪನೆ · Kalpane

A notes app for Android that combines Markdown text with stylus drawing.

Status: rewrite in progress. The previous version is kept on the `pre-rewrite` branch.

## Writing

Markdown renders as you type; the raw syntax shows only on the line you are editing. Supported: headings, **bold**, *italic*, ~~strike~~, `code`, code blocks, links, lists, checklists (tap to tick), quotes and rules. Enter continues a list; Enter on an empty item ends it. A toolbar above the keyboard has undo/redo and formatting.

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

CI builds the debug APK on every push to `main` and uploads it as a workflow artifact. Preview releases are published by running the **Publish preview APK** workflow with a version number; it creates the `preview-v<version>` tag and a GitHub prerelease.

## License

GPL-3.0-or-later for the application. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for the Mallige font and supplied artwork attribution.
