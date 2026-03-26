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
            name: 'RUN_UNIT_TESTS',
            defaultValue: true,
            description: 'Run JVM unit tests before connected checks.'
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
                }
            }
        }

        stage('Build Test APKs') {
            steps {
                sh './gradlew --no-daemon clean assembleDebug assembleDebugAndroidTest'
            }
        }

        stage('Unit Tests') {
            when {
                expression { return params.RUN_UNIT_TESTS }
            }
            steps {
                sh './gradlew --no-daemon testDebugUnitTest'
            }
        }

        stage('Connected Tests') {
            steps {
                sh './gradlew --no-daemon connectedDebugAndroidTest'
            }
        }
    }

    post {
        always {
            script {
                if (helpers != null) {
                    helpers.publishReports(this, false, true)
                }
            }
            cleanWs deleteDirs: true, disableDeferredWipeout: true
        }
    }
}

