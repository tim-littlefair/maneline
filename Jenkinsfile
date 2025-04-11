pipeline {
    agent any
    options {
        // Timeout counter starts AFTER agent is allocated
        timeout(time: 600, unit: 'SECONDS')
    }
    stages {
        stage('SDK check/rebuild/build') {
            steps {
                sh './scripts/rebuild_sdk.sh'
            }
        }
    }
}