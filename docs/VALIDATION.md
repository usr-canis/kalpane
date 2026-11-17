# Validation record

## Automated core checks

Local JDK 17, Android SDK 36, Gradle 8.13, AndroidX Ink 1.0.0.

- **20 JVM tests passed**: three-way page conflicts and independent edits, edit/delete recovery, mixed-layer ordering, atomic snapshot recovery with corrupt-original preservation, interrupted-write and image recovery, branch-local pending edits, conservative pen/touch policy, cancellation and navigation gating, remote path validation, GitHub preflight and server-side race rejection, non-forced ref updates and expected commit parents, private repository checks, branch name validation, portable archive round trip with typography/images, ZIP traversal rejection, and image checksum rejection.
- Android lint: checked locally. Results are also retained by CI.
- Debug and instrumented APK compilation: checked locally.
- Optimized preview APK: development signing and separate `dev.kalpane.preview` application ID. Signature and checksum recorded with release assets.

## UI verification

CI is configured to run API 29 phone and API 35 tablet emulators with KVM. Instrumented checks exercise notebook creation, text/undo/redo, themes, focus mode, Settings reachability, 48 dp pen target, switch semantics, synthetic pen cancellation, and fixture reload from local storage. The screenshot suite captures library, pages, mixed text/image/ink editor, serif/monospace settings, landscape, dark mode, and focus mode. It is repeated at 200% font scaling.

Execution results and any outstanding failures are recorded below before release. Configured tests are not automatically considered passed.

The local cloud machine has no `/dev/kvm`. A software emulator booted, but became unresponsive under concurrent compilation and optimization, so it was stopped. This is not recorded as a UI test pass.

## Hardware and service limits

**Physical stylus checks run: none.** No Redmi Pad Pro or other physical device is attached. Palm-before-pen, palm-during-pen, pressure, tilt, hover, screen-edge accidental controls, latency under backup, and physical multi-window/rotation checks are pending in [HARDWARE_TESTS.md](HARDWARE_TESTS.md).

GitHub backup conflict and HTTP behavior are verified with a controlled mock server. No notebook data was pushed to a live private backup repository; the user has not supplied a private test backup repository or credential. Source publication and release use the connected source repository, which is separate from the app's notebook backup feature.

Manual TalkBack traversal, hardware keyboard/switch access, and physical split-screen remain pending. Automated semantics, touch targets, responsive layouts, font scaling, rotation, and screenshots provide partial accessibility coverage, not a full accessibility certification.
