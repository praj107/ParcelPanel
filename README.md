ParcelPanel is an Android parcel-tracking app focused on Australian carriers, with offline-first local history, a normalized Room schema, a dark-mode Compose UI, and a local Jenkins pipeline setup for solo development.

## Implemented in this repo

- Kotlin + Jetpack Compose Android app scaffold
- Room-backed parcel, event, snapshot, and sync-session schema
- Carrier catalog and detector covering Australia Post, StarTrack, DHL, FedEx/TNT, UPS, Aramex Australia, CouriersPlease, Direct Freight Express, Team Global Express, and Toll
- External-tracker hand-off model for carriers that are not yet safe or practical to poll directly from a mobile-only client
- WorkManager-based periodic refresh scheduling
- Local CI assets: `Jenkinsfile`, connected pipeline, release pipeline, bootstrap Groovy, and release/version scripts

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

## Jenkins

Local Jenkins jobs created during execution:

- `ParcelPanel-CI`
- `ParcelPanel-Connected`
- `ParcelPanel-Release`

Repo assets for Jenkins live under `ci/jenkins/` and `scripts/ci/`.

For local Jenkins release publishing on this machine:

```bash
export JENKINS_USERNAME=...
export JENKINS_TOKEN=...
export GHO_TOKEN=...
scripts/ci/provision-jenkins-gh-credential.sh
```

Then run `ParcelPanel-Release` with `RELEASE_TYPE=major` to move the repo from `0.1.0` to `1.0.0`, build release artifacts, push the version commit and tag, and publish the GitHub release draft.
