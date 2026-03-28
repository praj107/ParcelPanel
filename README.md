ParcelPanel is an Android only parcel-tracking app focused on Australian carriers (for now), with offline-first local history, a normalized Room schema, and a dark Compose UI.

## Implemented in this repo

- Kotlin + Jetpack Compose Android app scaffold
- Room-backed parcel, event, snapshot, and sync-session schema
- Carrier catalog and detector covering Australia Post, StarTrack, DHL, FedEx/TNT, UPS, Aramex Australia, CouriersPlease, Direct Freight Express, Team Global Express, and Toll
- Hidden-WebView scraping of official public tracking pages when direct unauthenticated APIs are not practical on-device, with fallback to official tracker hand-off when extraction fails
- WorkManager-based periodic refresh scheduling
- In-app OTA update checks against the public GitHub Releases feed, with SHA-256 and signing-certificate validation before install
- Local CI assets: `Jenkinsfile`, connected pipeline, release pipeline, bootstrap Groovy, and release/version scripts. (As a bonus)

## Local build

Use a full JDK path on this machine. The current repo was verified with:

```bash
export JAVA_HOME=/home/pranav/.sdkman/candidates/java/22.0.2-tem
export ANDROID_HOME=/home/pranav/Android/Sdk
export ANDROID_SDK_ROOT=/home/pranav/Android/Sdk
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew --no-daemon testDebugUnitTest assembleDebug
./gradlew --no-daemon lintDebug
./gradlew --no-daemon connectedDebugAndroidTest
```

## Signed release build

Generate a local baseline keystore once:

```bash
scripts/ci/generate-baseline-signing.sh
```

That writes a gitignored `release-signing.properties` file for local builds. After that:

```bash
export JAVA_HOME=/home/pranav/.sdkman/candidates/java/22.0.2-tem
export ANDROID_HOME=/home/pranav/Android/Sdk
export ANDROID_SDK_ROOT=/home/pranav/Android/Sdk
./scripts/build-release.sh
scripts/ci/package-release.sh
```

The packaged public release output is intentionally limited to:

- `ParcelPanel-vX.Y.Z.apk`
- `SHA256SUMS.txt`

## OTA smoke coverage

The repo now includes an emulator-based OTA smoke test on the minimum supported Android level. It uses an API 26 emulator and serves a fake GitHub release feed from loopback inside the instrumentation process, then verifies that ParcelPanel can:

- discover a newer release
- download the APK
- validate `SHA256SUMS.txt`
- confirm the APK matches the installed signing certificate
- reach `READY_TO_INSTALL`

Local command:

```bash
./gradlew --no-daemon testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin
scripts/ci/run-api26-connected-tests.sh
```

## GitHub Actions

Two GitHub workflows mirror the local/Jenkins release model:

- `.github/workflows/android-verify.yml`
  - JVM tests and debug build
  - API 26 emulator OTA smoke test
- `.github/workflows/android-release.yml`
  - manual `workflow_dispatch`
  - runs verification first
  - bumps `version.properties`
  - builds the signed release APK
  - packages `ParcelPanel-vX.Y.Z.apk` and `SHA256SUMS.txt`
  - pushes the release commit/tag
  - publishes the GitHub release consumed by the in-app OTA client

Required GitHub secrets for the release workflow:

- `PARCELPANEL_SIGNING_STORE_B64`
- `PARCELPANEL_SIGNING_STORE_PASSWORD`
- `PARCELPANEL_SIGNING_KEY_ALIAS`
- `PARCELPANEL_SIGNING_KEY_PASSWORD`
