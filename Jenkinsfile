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
                        sh """
                            ./scripts/build_fhau_release.sh $RELEASE_VERSION_MAJOR $RELEASE_VERSION_MINOR $RELEASE_VERSION_PATCH
                        . ../$SDK_NAME/fhau_sdk_vars.sh
                        ./gradlew clean build
                        """
                    } else {
                        echo 'Not a release'
                        . ../$SDK_NAME/fhau_sdk_vars.sh
                        ./gradlew clean build
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