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
        booleanParam(
            name: 'RUN_LINT',
            defaultValue: true,
            description: 'Run Android lint as part of CI validation.'
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
                    helpers.bootstrapAndroid(this, params.ANDROID_SDK_ROOT_OVERRIDE, false)
                    def versionInfo = helpers.currentVersion(this)
                    env.RELEASE_VERSION = versionInfo.VERSION_NAME
                    env.RELEASE_TAG = versionInfo.VERSION_TAG
                    currentBuild.displayName = "#${env.BUILD_NUMBER} verify ${env.RELEASE_TAG}"
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
    }

    post {
        always {
            script {
                if (helpers != null) {
                    helpers.publishReports(this, false, false)
                } else {
                    junit allowEmptyResults: true, testResults: 'app/build/test-results/testDebugUnitTest/*.xml'
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'app/build/reports/**'
                }
            }
            cleanWs deleteDirs: true, disableDeferredWipeout: true
        }
    }
}

