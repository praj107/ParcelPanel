def parseKeyValueBlock(String raw) {
    raw
        .trim()
        .split('\n')
        .collectEntries { line ->
            def parts = line.split('=', 2)
            [(parts[0]): parts.length > 1 ? parts[1] : '']
        }
}

def resolveSdkRoot(script, String overrideValue = '') {
    def sdkRoot = overrideValue?.trim()
    if (!sdkRoot) {
        sdkRoot = script.env.ANDROID_SDK_ROOT ?: script.env.ANDROID_HOME
    }
    if (!sdkRoot) {
        script.error('ANDROID_SDK_ROOT is not configured on this Jenkins node.')
    }
    script.env.EFFECTIVE_ANDROID_SDK_ROOT = sdkRoot
    script.writeFile file: 'local.properties', text: "sdk.dir=${sdkRoot}\n"
    return sdkRoot
}

def bootstrapAndroid(script, String sdkRootOverride = '', boolean requireGh = true) {
    resolveSdkRoot(script, sdkRootOverride)
    script.sh 'chmod +x gradlew scripts/build-release.sh scripts/ci/*.sh'

    def ghCheck = requireGh ? 'gh --version' : 'true'
    script.sh """
        set -e
        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
        test -d "\$EFFECTIVE_ANDROID_SDK_ROOT/platforms/android-35"
        test -x "\$EFFECTIVE_ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner"
        java -version
        ${ghCheck}
    """
}

def currentVersion(script) {
    parseKeyValueBlock(script.sh(
        returnStdout: true,
        script: 'scripts/ci/current-version.sh'
    ))
}

def bumpVersion(script, String bumpType) {
    parseKeyValueBlock(script.sh(
        returnStdout: true,
        script: "scripts/ci/bump-version.sh ${bumpType}"
    ))
}

def publishReports(script, boolean includeReleaseArtifacts = false, boolean includeAndroidTests = false) {
    def testPatterns = ['app/build/test-results/testDebugUnitTest/*.xml']
    if (includeAndroidTests) {
        testPatterns << 'app/build/outputs/androidTest-results/**/*.xml'
        testPatterns << 'app/build/reports/androidTests/connected/**/*.xml'
    }

    def artifacts = ['app/build/reports/**']
    if (includeAndroidTests) {
        artifacts << 'app/build/outputs/androidTest-results/**'
    }
    if (includeReleaseArtifacts) {
        artifacts << 'app/build/outputs/apk/**'
        artifacts << 'app/build/outputs/bundle/**'
        artifacts << 'app/build/outputs/mapping/**'
        artifacts << 'releases/**'
    }

    script.junit allowEmptyResults: true, testResults: testPatterns.join(', ')
    script.archiveArtifacts allowEmptyArchive: true, artifacts: artifacts.join(', ')

    if (script.fileExists('app/build/reports/lint-results-debug.xml')) {
        script.recordIssues(
            enabledForFailure: true,
            id: 'android-lint-debug',
            name: 'Android Lint Debug',
            tools: [script.androidLintParser(pattern: 'app/build/reports/lint-results-debug.xml')]
        )
    }
}

return this

