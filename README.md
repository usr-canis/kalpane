# ಕಲ್ಪನೆ · Kalpane

A native Android engineer’s notebook. Kotlin, Jetpack Compose, fixed Material 3 themes, and **stable AndroidX Ink 1.0.0**. Android 10 / API 29 and later; phones, tablets, rotation, and resizable windows.

## Build

Open this folder in Android Studio with JDK 17 and Android SDK 36, or run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The Gradle wrapper pins and checks the Gradle distribution checksum. No account is needed to write or save locally.

## Use

Create a named notebook, then open its pages. The library always opens first; each notebook offers the last page. Page menus rename and reorder pages. The editor has one 48 dp tool strip, and Focus mode leaves one pen button. Secondary tools and thumbnails open on demand.

- **Pen:** pressure-sensitive vector ink. **Stroke eraser:** remove an entire intersected ink stroke. Undo and redo cover text, ink, image placement, ordering, and page edits.
- **Text:** add text, then use Select and long-press → Edit text to revise it. Kannada text uses bundled Unicode Mallige.
- **Images:** More → Import image uses Android Photo Picker. Originals are copied to private app storage before the page references them. Select → drag to move; drag the corner to resize. Long-press for ordering, locking, removal, and numeric position/size controls. The page-objects panel also reaches covered and locked objects.
- **Navigation:** More → Navigate enables swipe page turns at fit size, and one-finger pan while zoomed. Two-finger pinch and pan work without enabling finger drawing. The page buttons work at every zoom. Page coordinates do not change with viewport size.
- **Input:** pen-only is the default. Enable finger drawing explicitly. Touch is suppressed while the pen is active and for 700 ms afterwards; a touch sequence contaminated by pen activity remains blocked. Android `ACTION_CANCEL` and `FLAG_CANCELED` discard the stroke. Hover supplies a cursor when reported by the hardware.
- **Settings:** System / Light / Dark, paper, grid spacing, default pen width, input, reduced motion, and GitHub backup. Dynamic wallpaper colors are deliberately disabled. Light paper is `#FBF9F4`; amber `#E0730C` is the brand accent, with darker accessible control text.

## Offline storage and backup

Local saves run through one IO writer, with fsync and atomic replacement. The previous complete snapshot is retained. Recovery preserves corrupt originals instead of silently resetting notebooks. Image assets use SHA-256 filenames; app storage contains the originals, not expiring Photo Picker URIs. Clearing app storage or uninstalling removes unbacked local data. OS cloud backup is disabled to exclude repository credentials.

GitHub backup is explicit, not automatic network synchronization:

1. Create a **private** repository initialized with a README.
2. Create a fine-grained token restricted to that repository, with **Contents: read and write**.
3. In Settings, enter `owner/repository` and the token, then **Verify connection**.
4. **Pull** establishes the branch head and merges local work. **Push** writes a Git commit under `kalpane/`, retaining unrelated repository files.
5. **Create branch** starts at the remote branch head. **Switch branch** retains each branch’s independent local workspace and pending edits.

Tokens are encrypted with AES-256-GCM using an Android Keystore key, with the repository as authenticated context. Tokens are absent from page data, logs, screenshots, and saved UI state. Use Remove stored credential to disconnect the credential on this device.

A push first compares the stored head, uploads content, checks again, then makes a **non-forced** ref update with the expected head as its commit parent. A concurrent remote commit makes the update fail instead of replacing it. Pull uses a three-way merge; when both sides change the same page, the local version stays and a separately named remote conflict page is added. Text, ink, images, ordering, and metadata travel together. Images are hash-checked before a pulled document is applied.

## CI and releases

[Android CI](.github/workflows/ci.yml) builds the debug APK, runs JVM tests and Android lint, and runs instrumented tests on API 29 phone and API 35 tablet profiles. Reports and APKs are uploaded as workflow artifacts. Tests run for pull requests and pushes to `main`.

[Signed Android release](.github/workflows/release.yml) runs on `v*` tags or manually. Configure the `release` GitHub environment with:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `ANDROID_STORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Release key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

The workflow validates secrets, tests, signs an APK and AAB, verifies the APK signature, and creates a **draft GitHub release** on version tags. Missing signing secrets fail the job; there is no debug-key fallback. Keep the same signing key for future updates. Play Store upload is not configured because no Play Console application or service account has been supplied.

## Verification and hardware

See [validation results](docs/VALIDATION.md), [hardware acceptance plan](docs/HARDWARE_TESTS.md), and [architecture](docs/ARCHITECTURE.md). **No physical stylus checks have been run.** Emulator and synthetic input tests cannot certify Redmi Pad Pro palm rejection, pressure accuracy, tilt, hover, or writing latency. These remain release acceptance gates.

The logo was prepared from the supplied artwork into a transparent square. The OS splash uses an inset asset to accommodate the adaptive mask; the branded launch and library show the Kannada name before the logo. The original artwork should be checked against the packaged cleanup before a brand release.

## License

GPL-3.0-or-later for the application. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for the Mallige font and supplied artwork attribution.

## Typography, search, and portable copies

Settings → Text & typography offers **Sans**, **Source Serif 4**, and **JetBrains Mono**, with a live preview and default size. Each text object's choice is saved in its page. Use the object panel to change one text object's font, or apply Settings typography to all text on the current page. Mixed Kannada strings retain Mallige through an explicit font fallback chain.

The library search finds notebook names, page names, and editable text. Settings → Portable notebooks exports a credential-free ZIP with all current-branch notebooks and original images. Import verifies checksums and creates separate notebook copies instead of replacing existing work. Portable backups are limited to 200 MB uncompressed, 40 MB metadata, and 20 MB per image.

### Preview release automation

The first preview is development-signed, uses `dev.kalpane.preview`, and can be installed alongside production. Subsequent automated previews use [Publish preview APK](.github/workflows/preview.yml), dispatched against an existing `preview-v*` tag. Set `PREVIEW_KEYSTORE_BASE64` to the **same** development keystore used for the first preview, so Android accepts updates without uninstalling data. The standard Android development alias/password are used; this key is unsuitable for production. The current connected GitHub integration cannot manage Actions secrets, so that secret requires repository-owner setup. The separate production release workflow requires the four production signing secrets described above.
