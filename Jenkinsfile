pipeline {
    agent any
    options {
        // Timeout counter starts AFTER agent is allocated
        timeout(time: 1200, unit: 'SECONDS')
    }
    stages {
        stage('SDK check/rebuild/build') {
            when {
                expression {
                    return params.REBUILD_SDK == true
                }
            }
            steps {
                echo "Rebuilding FHAU SDK in ../$SDK_NAME"
                sh "sh ./scripts/rebuild_sdk.sh $SDK_NAME"
            }
        }
        stage('FHAU CI Build') {
            steps {
                script {
                    if( params.RELEASE_VERSION_MAJOR != "" ) {
                        sh './scripts/build_fhau_release.sh'
                    } else {
                        echo 'Not a release'
                    }
                    sh """
                        . ../$SDK_NAME/fhau_sdk_vars.sh
                        ./gradlew clean build
                    """
                }
            }
        }
    }
    post {
        success {
            archiveArtifacts
                artifacts: 'android-app/build/outputs/**/*.apk,desktop-app/**/*.jar'
                fingerprint: true
            }
    }
}