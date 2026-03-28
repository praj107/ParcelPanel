def helpers = null

pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        skipStagesAfterUnstable()
        timestamps()
    }

    parameters {
        choice(
            name: 'RELEASE_TYPE',
            choices: ['patch', 'minor', 'major', 'chore'],
            description: 'Version bump policy before release packaging.'
        )
        booleanParam(
            name: 'RUN_LINT',
            defaultValue: true,
            description: 'Run Android lint in release validation.'
        )
        booleanParam(
            name: 'RUN_CONNECTED_TESTS',
            defaultValue: false,
            description: 'Run connected tests if an emulator or device is available.'
        )
        booleanParam(
            name: 'FORCE_RELEASE_TAG_UPDATE',
            defaultValue: false,
            description: 'Allow force-updating an existing release tag when repairing a partial release.'
        )
        string(
            name: 'ANDROID_SDK_ROOT_OVERRIDE',
            defaultValue: '',
            description: 'Optional Android SDK override.'
        )
    }

    environment {
        GRADLE_USER_HOME = "${WORKSPACE}/.gradle-home"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    helpers = load 'ci/jenkins/pipelines/helpers.groovy'
                }
            }
        }

        stage('Bootstrap') {
            steps {
                script {
                    helpers.bootstrapAndroid(this, params.ANDROID_SDK_ROOT_OVERRIDE, true)
                }
            }
        }

        stage('Prepare Version') {
            steps {
                script {
                    def versionInfo = helpers.bumpVersion(this, params.RELEASE_TYPE)
                    env.RELEASE_VERSION = versionInfo.VERSION_NAME
                    env.RELEASE_TAG = versionInfo.VERSION_TAG
                    currentBuild.displayName = "#${env.BUILD_NUMBER} release ${env.RELEASE_TAG}"
                }
            }
        }

        stage('Quality Gates') {
            steps {
                sh './gradlew --no-daemon clean testDebugUnitTest assembleDebug'
            }
        }

        stage('Lint') {
            when {
                expression { return params.RUN_LINT }
            }
            steps {
                sh './gradlew --no-daemon lintDebug'
            }
        }

        stage('Connected Tests') {
            when {
                expression { return params.RUN_CONNECTED_TESTS }
            }
            steps {
                sh './gradlew --no-daemon connectedDebugAndroidTest'
            }
        }

        stage('Build Release Assets') {
            steps {
                withCredentials([
                    file(credentialsId: 'parcelpanel-android-keystore', variable: 'PARCELPANEL_SIGNING_STORE_FILE'),
                    string(credentialsId: 'parcelpanel-android-store-password', variable: 'PARCELPANEL_SIGNING_STORE_PASSWORD'),
                    string(credentialsId: 'parcelpanel-android-key-alias', variable: 'PARCELPANEL_SIGNING_KEY_ALIAS'),
                    string(credentialsId: 'parcelpanel-android-key-password', variable: 'PARCELPANEL_SIGNING_KEY_PASSWORD'),
                ]) {
                    sh './scripts/build-release.sh'
                    sh 'scripts/ci/package-release.sh --version "$RELEASE_VERSION"'
                }
            }
        }

        stage('Publish Release') {
            steps {
                withCredentials([
                    string(credentialsId: 'parcelpanel-gh-token', variable: 'GH_TOKEN')
                ]) {
                    sh '''
                        export RELEASE_BRANCH="${BRANCH_NAME:-main}"
                        export FORCE_RELEASE_TAG_UPDATE="${FORCE_RELEASE_TAG_UPDATE}"
                        scripts/ci/push-release-refs.sh
                        scripts/ci/publish-github-release.sh
                    '''
                }
            }
        }
    }

    post {
        always {
            script {
                if (helpers != null) {
                    helpers.publishReports(this, true, params.RUN_CONNECTED_TESTS)
                }
            }
            cleanWs deleteDirs: true, disableDeferredWipeout: true
        }
    }
}
