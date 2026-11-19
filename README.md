# ಕಲ್ಪನೆ · Kalpane

A notes app for Android that combines Markdown text with stylus drawing.

Status: rewrite in progress. The previous version is kept on the `pre-rewrite` branch.

## Using it

The app opens on your notebooks. A notebook holds pages, and each page combines Markdown text with a drawing layer.

- **Text:** tap with a finger to type. Markdown renders as you type; raw syntax shows only on the line you are editing. Headings, emphasis, links, lists, checklists (tap to tick), quotes, rules and fenced code blocks with basic syntax colouring.
- **Drawing:** the stylus draws anywhere on the page: pen, highlighter, eraser (whole or partial strokes). Fingers type and scroll unless *Draw with finger* is on in Settings.
- **Shapes:** line, arrow, rectangle, ellipse. Select one to move, resize or rotate it.
- **Images:** insert from the photo picker, then move, resize, rotate, bring forward, send backward or delete.
- **Select:** lasso strokes, shapes and images to move, scale, rotate, reorder or delete them together.
- Undo and redo cover text and drawing. Pinch to zoom.
- **Search** every page from the home screen; **Recent** jumps back to the latest pages.
- **Export** a page as PDF or PNG, or a notebook as one PDF, through the share sheet.
- **Trash:** deleted pages and notebooks can be restored for 30 days.
- The pen draws through AndroidX Ink's low-latency overlay while the stylus is down.

## How pages are stored

```
<notebook>/notebook.json          page order
<notebook>/<page>.md              text
<notebook>/<page>.canvas.json     strokes, shapes, image placement
<notebook>/assets/<sha256>.<ext>  images
```

Pages use a fixed 800-unit width, so drawings stay aligned with text on every screen. The same layout is used in your private notes repository.

## Sync with GitHub

1. Create a **private** repository with a README.
2. Create a [fine-grained token](https://github.com/settings/personal-access-tokens/new) for only that repository, with **Contents: read and write**.
3. In Settings → Sync, enter `owner/repository`, the branch and the token.
4. Tap the sync button on the home screen.

Sync pulls, merges and pushes in one commit. Notebooks are folders at the top of the repository; files outside notebook folders (such as the README) are left alone. If a page changed both here and on GitHub, your version is kept and GitHub's version is saved alongside it as “(conflict)”. The branch is never force-pushed. *Back up automatically* pulls and pushes every 6 hours on Wi-Fi, and waits while a page is open. The token is encrypted with the Android Keystore and never leaves the device except in requests to api.github.com.

## Build

JDK 17 and Android SDK 36:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

CI builds the debug APK on every push to `main` and uploads it as a workflow artifact. Preview releases are published by running the **Publish preview APK** workflow with a version number; it creates the `preview-v<version>` tag and a GitHub prerelease.

## License

GPL-3.0-or-later for the application. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for the Mallige font and supplied artwork attribution.
