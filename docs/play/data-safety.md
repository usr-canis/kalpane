# Play Console: Data safety answers

These match how the app works today. Review them before submitting; you are responsible for the declaration.

**Does your app collect or share any of the required user data types?**
Yes, only when the user adds a GitHub remote (optional). Without a remote, no data leaves the device.

**Data types** (only with a GitHub remote):
| Type | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Files and docs (notebook files) | Yes, sent to the user's own GitHub repository | No | App functionality (sync) | Yes |
| Photos (images placed on pages) | Yes, same as above | No | App functionality (sync) | Yes |

- **Is all data encrypted in transit?** Yes (HTTPS only; cleartext traffic is disabled).
- **Can users request that data be deleted?** Yes: remove the remote in Settings and delete the repository files on GitHub; uninstalling deletes local data.
- **Ephemeral processing?** No.
- **Data collected by the developer?** None. There is no developer server, analytics, advertising or crash reporting service.

**Google ML Kit (handwriting and shapes)**: recognition runs on the device and models are downloaded from Google. ML Kit may collect diagnostic and usage data (device information, API performance) for Google. Check Google's current "ML Kit data disclosure" guidance and declare what it lists (typically *App info and performance → Diagnostics* and *Device or other IDs*, collected, not shared, for analytics), as the SDK's behaviour is controlled by Google.

Other answers
- **Ads**: No.
- **Target audience**: 18+ (or 13+), not designed for children.
- **Content rating questionnaire**: Utility/productivity; no user-to-user communication, no location, no purchases.
- **App access**: All features available without login; GitHub remote is optional.
- **Permissions**: INTERNET only (photo picker needs no permission).
