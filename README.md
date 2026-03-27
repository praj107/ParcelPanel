ParcelPanel is an Android only parcel-tracking app focused on Australian carriers (for now), with offline-first local history, a normalized Room schema, a simplistic classic dark themed UI,.

## Implemented in this repo

- Kotlin + Jetpack Compose Android app scaffold
- Room-backed parcel, event, snapshot, and sync-session schema
- Carrier catalog and detector covering Australia Post, StarTrack, DHL, FedEx/TNT, UPS, Aramex Australia, CouriersPlease, Direct Freight Express, Team Global Express, and Toll
- External-tracker hand-off model for carriers that are not yet safe or practical to poll directly from a mobile-only client
- WorkManager-based periodic refresh scheduling
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
