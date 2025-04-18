pipeline {
    agent any
    options {
        // Timeout counter starts AFTER agent is allocated
        timeout(time: 600, unit: 'SECONDS')
    }
    stages {
        stage('SDK check/rebuild/build') {
            steps {
                if ( params.rebuild_sdk  ) {
                    sh 'echo Rebuilding FHAU SDK'
                    sh 'sh ./scripts/rebuild_sdk.sh'
                } else {
                    sh 'echo Using existing FHAU SDK'
                }
            }
        }
        stage('FHAU CI build') {
            steps {
                sh './gradlew build'
            }
        }
        stage('FHAU Release build') {
            steps {
                sh './scripts/build_fhau_release.sh'
            }
        }
    }
}