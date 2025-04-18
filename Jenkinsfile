pipeline {
    agent any
    options {
        // Timeout counter starts AFTER agent is allocated
        timeout(time: 600, unit: 'SECONDS')
    }
    stages {
        stage('SDK check/rebuild/build') {
            steps {
                steps {
                    if(params.REBUILD_SDK == true) {
                        echo Rebuilding FHAU SDK
                        sh 'sh ./scripts/rebuild_sdk.sh'
                    } else {
                        echo Using existing FHAU SDK
                    }
                }
            }
        }
        stage('FHAU Release Patch') {
            steps {
                if(params.RELEASE_VERSION_MAJOR == "") {
                    sh './scripts/build_fhau_release.sh'
                }
            }
        }

        stage('FHAU CI build') {
            steps {
                sh './gradlew build'
            }
        }
    }
}