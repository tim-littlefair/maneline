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
                echo "Rebuilding maneline SDK in $SDK_PATH"
                sh "sh ./scripts/rebuild_sdk.sh $SDK_PATH"
            }
        }
        stage('Maneline CI Build') {
            steps {
                script {
                    if( params.RELEASE_VERSION_MAJOR != "" ) {
                        sh """
                            echo 'Release!'
                            . $SDK_PATH/maneline_sdk_vars.sh
                            . ./scripts/build_maneline_release.sh --build-signed-bundle
                        """
                    } else {
                        echo 'Not a release'
                        sh """
                            . $SDK_PATH/maneline_sdk_vars.sh
                            ./gradlew clean build
                        """
                    }
                }
            }
        }
    }
    post {
        success {
            script {
                archiveArtifacts 'android-app/build/outputs/**/*.apk'
                archiveArtifacts 'desktop-app/**/*.jar'
            }
        }
    }
}