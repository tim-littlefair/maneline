pipeline {
    agent any
    options {
        // Timeout counter starts AFTER agent is allocated
        timeout(time: 600, unit: 'SECONDS')
    }
    stages {
        stage('SDK check/rebuild/build') {
            steps {
                when {
                    expression { params.REBUILD_SDK == true  }
                }
                steps {
                    sh 'echo Rebuilding FHAU SDK'
                    sh 'sh ./scripts/rebuild_sdk.sh'
                }
            }
        }
        stage('FHAU Release Patch') {
            when {
                expression { params.RELEASE_VERSION_MAJOR != "" }
            }
            steps {
                sh './scripts/build_fhau_release.sh'
            }
        }

        stage('FHAU CI build') {
            steps {
                sh './gradlew build'
            }
        }
    }
}