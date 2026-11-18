# Validation record

## Automated core checks

Local JDK 17, Android SDK 36, Gradle 8.13, AndroidX Ink 1.0.0.

- **20 JVM tests passed**: three-way page conflicts and independent edits, edit/delete recovery, mixed-layer ordering, atomic snapshot recovery with corrupt-original preservation, interrupted-write and image recovery, branch-local pending edits, conservative pen/touch policy, cancellation and navigation gating, remote path validation, GitHub preflight and server-side race rejection, non-forced ref updates and expected commit parents, private repository checks, branch name validation, portable archive round trip with typography/images, ZIP traversal rejection, and image checksum rejection.
- Android lint: checked locally. Results are also retained by CI.
- Debug and instrumented APK compilation: checked locally.
- Optimized preview APK: development signing and separate `dev.kalpane.preview` application ID. Signature and checksum recorded with release assets.

## UI verification

CI is configured to run API 29 phone and API 35 tablet emulators with KVM. Instrumented checks exercise notebook creation, text/undo/redo, themes, focus mode, Settings reachability, 48 dp pen target, switch semantics, synthetic pen cancellation, and fixture reload from local storage. The screenshot suite captures library, pages, mixed text/image/ink editor, serif/monospace settings, landscape, dark mode, and focus mode. It is repeated at 200% font scaling.

On 18 November 2026, [the device jobs in run 44242211163](https://github.com/usr-canis/kalpane/actions/runs/44242211163) passed all **10 instrumented checks on each device**, then passed the additional layout run at **200% font scaling on each device**: 22 successful instrumented executions, no skipped tests. This includes native ink reconstruction after offline reload, completed pen strokes, whole-gesture cancellation, cancelled palm pointers, stylus image moves and position locks, and EXIF-oriented image decoding. The build job caught an Activity-context lint rule; the final change uses Compose's `LocalActivity` and adds an assertion for dark status-bar contrast. Release notes link the final CI run.

The phone and tablet fixture screenshots were inspected for mixed-page rendering, Kannada, serif/monospace text, focus mode, rotation, dark mode, and large text. At 200% text size in phone landscape, the fit-to-page overview is small; Focus mode and pinch zoom provide more writing space. These are synthetic fixture pages, not user notebooks. Original screenshots and JUnit reports are retained in CI artifacts.

The local cloud machine has no `/dev/kvm`. A software emulator booted, but became unresponsive under concurrent compilation and optimization, so it was stopped. This is not recorded as a UI test pass.

## Hardware and service limits

**Physical stylus checks run: none.** No Redmi Pad Pro or other physical device is attached. Palm-before-pen, palm-during-pen, pressure, tilt, hover, screen-edge accidental controls, latency under backup, and physical multi-window/rotation checks are pending in [HARDWARE_TESTS.md](HARDWARE_TESTS.md).

GitHub backup conflict and HTTP behavior are verified with a controlled mock server. No notebook data was pushed to a live private backup repository; the user has not supplied a private test backup repository or credential. Source publication and release use the connected source repository, which is separate from the app's notebook backup feature.

Manual TalkBack traversal, hardware keyboard/switch access, and physical split-screen remain pending. Automated semantics, touch targets, responsive layouts, font scaling, rotation, and screenshots provide partial accessibility coverage, not a full accessibility certification.
