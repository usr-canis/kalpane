# Physical device acceptance — pending

No physical Android device is attached to this workspace. **None of the physical checks below has run.** Record device model, OS/build, pen model/firmware, refresh rate, app commit, input settings, and a screen/video capture when executing them. An emulator passing synthetic events does not count as hardware evidence.

## Required matrix

| Device | Stylus | Status |
| --- | --- | --- |
| Redmi Pad Pro (user's device) | Compatible Redmi Smart Pen; record exact model | Not run |
| Samsung Galaxy Tab with S Pen | Active S Pen | Not run |
| Another Android tablet/phone with active stylus | Record brand and protocol | Not run |
| Android phone without active stylus | Finger-only input | Not run |

## Release-blocking palm checks

For pen-only and finger drawing modes, each at fit, 2× zoom, portrait, landscape, and split screen:

1. Rest palm first, then bring pen into hover and write for 30 seconds. Lift the pen before lifting the palm. There must be no extra committed marks, object moves, or page turns.
2. Start writing, then rest/move/lift the palm while the pen remains down. Repeat with the palm crossing the page navigation area and tool strip. No accidental tool activation or page turn is allowed.
3. Hover while touching with the palm; bring the pen down; change angle and pressure; exit hover. Check cooldown without dropping the first intentional post-pen gesture.
4. Trigger system cancellation via navigation gesture, notification shade, rotation, and multi-window transition during a stroke. Cancelled segments must never appear after relaunch or backup.
5. Enable finger drawing and draw intentionally, then add a second finger for pinch. The provisional one-finger stroke must be cancelled. Repeat stylus takeover while a finger is already down.
6. Verify a deliberately selected Navigate swipe works after pen activity has ended; it must not turn while the pen is down or the touch sequence was contaminated.

Hardware may report a passive capacitive pen as a finger; it cannot provide active-pen palm separation, pressure, tilt, or hover. Report this as unsupported hardware capability rather than fabricating values.

## Responsiveness and fidelity

- Record pressure range and brush variation from light to firm contact. Record tilt/orientation availability and hover cursor range. The native event path preserves reported axes; do not infer capabilities from brand alone.
- Write rapid spirals and crosshatching while autosave writes and while a large-image GitHub push runs. Capture latency with a high-speed camera and report median/p95, refresh rate, and frame drops. No latency measurements have been made yet.
- Draw over and under images and text; reorder every kind; lock objects; resize at multiple zooms; relaunch and restore on a second-size device. Verify coordinates and stacking remain identical.
- Remove connectivity mid-stroke/import/save/push; force-stop after an acknowledged save; reopen offline. An acknowledged local save and copied image must survive. Resume backup without duplicates or lost pages.
- Edit the same page independently on two devices. Push A, reject stale push B, pull B, inspect both versions, push merged B. Repeat with branch switching and an interrupted network request.
- Run TalkBack traversal, external keyboard navigation, 200% font scaling, display scaling, both themes, high contrast, and reduced motion. All controls must remain reachable and described. Check the Kannada name and logo on the physical launcher/splash mask.
