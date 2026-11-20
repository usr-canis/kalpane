# Releasing Kalpane to Google Play

## One-time setup

1. **Upload key.** Add these repository secrets in a GitHub environment named `release`
   (Settings → Environments → New environment `release` → Add secret):
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
   Keep a private copy of the keystore and passwords; Play App Signing holds the app signing key,
   and this upload key can be reset through Play support if lost.
2. **Privacy policy.** Play needs a public URL. Publish `docs/PRIVACY.md` somewhere public
   (for example a public GitHub Gist, or GitHub Pages on a public repository) and paste the URL in
   Play Console → Policy → App content → Privacy policy, and in the store listing.
3. **Create the app** in Play Console: name Kalpane, app, free. Package name `dev.kalpane`.
4. **Store listing** from `docs/play/listing.md`; graphics from the `ci-screenshots` branch:
   icon 512×512 (export `app/src/main/res/drawable/kalpane_logo.png`), feature graphic
   `play-feature-graphic.png`, phone and tablet screenshots.
5. **App content**: Data safety from `docs/play/data-safety.md`, content rating questionnaire,
   target audience, ads: none.

## Each release

1. Run **Actions → Release → Run workflow** with the version (for example `1.0.0`).
   It tests, builds a signed AAB and APK, verifies the signature and 16 KB alignment,
   and creates a draft GitHub release `v<version>` with the files.
2. First release only: upload the AAB from the draft release to Play Console → Testing →
   Internal testing → Create release, by hand.
3. Later releases upload to internal testing automatically if `PLAY_SERVICE_ACCOUNT_JSON`
   (a Play service account key with release permissions) is added to the `release` environment.
4. Test from the internal track on your devices, then promote to Production in Play Console.
