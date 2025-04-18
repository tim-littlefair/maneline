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
        stage('FHAU CI Build') {
            steps {
                script {
                    if(not params.RELEASE_VERSION_MAJOR == "") {
                        sh './scripts/build_fhau_release.sh'
                    } else {
                        echo 'Not a release'
                    }
                    sh 'exit 1'
                }
            }
        }

        stage('FHAU CI build') {
            steps {
                sh """
                    . ../$SDK_NAME/fhau_sdk_vars.sh
                    ./gradlew build
                """
            }
        }
    }
}