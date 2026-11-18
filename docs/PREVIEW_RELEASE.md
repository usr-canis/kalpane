Kalpane is a native Android engineer’s notebook built with Kotlin, Compose, Material 3, and stable AndroidX Ink 1.0.0.

**This is a development-signed preview, not a production release.** Its application ID is `dev.kalpane.preview`, so it can coexist with a future production app. The development signing key is not a production trust boundary. Export your notebooks before moving between preview and production.

Included: named notebooks and ordered pages; mixed text/vector-ink/image layers; Photo Picker import; placement, resizing, locking and ordering; undo/redo; HSV/HEX/RGB/opacity color picker; pen-only and finger input; pinch/pan and deliberate page navigation; focus mode; fixed themes; Mallige Kannada; Source Serif 4 and JetBrains Mono text settings; offline atomic autosave; portable ZIP export/import; and private GitHub repository backup with branch-local edits, race-guarded pushes, and conflict copies.

Android 10 or newer is required. Install the attached APK. SHA256SUMS.txt verifies the downloaded package. A private GitHub backup repository must be initialized with a README and accessed with a fine-grained Contents read/write token.

Validation includes 20 JVM tests, Android lint, 10 instrumented checks on each of an Android 10 phone and Android 15 tablet emulator, and an additional layout run at 200% font scaling on both. The review fixed first-stroke initialization, cancelled-palm handling, stylus image-position commits, camera orientation, and reloading ink without device calibration. Push and Pull run in the background while the editor remains accessible.

CI and release workflows are included. Automated preview updates require the repository owner to add `PREVIEW_KEYSTORE_BASE64`; the connected integration cannot manage Actions secrets. Production releases require a separate production signing key. This first preview is built and signed with the retained development key.

**No stylus checks have run on physical hardware.** Redmi Pad Pro palm rejection, pressure/tilt/hover fidelity, and latency remain unverified. Do not treat synthetic event or emulator results as hardware certification. Review `docs/VALIDATION.md` and `docs/HARDWARE_TESTS.md` in the source for exact coverage and remaining checks.
