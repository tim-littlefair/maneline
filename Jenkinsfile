pipeline {
    agent any
    options {
        // Timeout counter starts AFTER agent is allocated
        timeout(time: 600, unit: 'SECONDS')
    }
    stages {
        stage('SDK check/rebuild/build') {
            when {
                expression {
                    return params.REBUILD_SDK == true
                }
            }
            steps {
                echo "Rebuilding FHAU SDK"
                sh 'sh ./scripts/rebuild_sdk.sh'
            }
        }
        stage('FHAU Release Patch') {
            steps {
                script {
                    if(params.RELEASE_VERSION_MAJOR == "") {
                        sh './scripts/build_fhau_release.sh'
                    } else {
                        echo 'Not a release'
                    }
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