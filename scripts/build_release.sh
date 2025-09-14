#! /bin/bash

# This is a quick and dirty script to build and run a release of Maneline
set -e

git restore build.gradle
git restore deployment/balena/balena.yml

# This script is run by Jenkins, which is expected to have set
# the three string parameters RELEASE_VERSION_{MAJOR,MINOR,PATCH}
# into environment variables for the run, and is also expected
# to have defined an environment variable BUILD_ID containing
# Jenkins' own run number for this build.

export releaseString=$RELEASE_VERSION_MAJOR.$RELEASE_VERSION_MINOR.$RELEASE_VERSION_PATCH
export gitHash=$(git rev-parse HEAD | cut -c 1-7)
export gitUncleanFileCount=$(git diff --name-only | wc -l)
export buildId=$(printf "%04d" "$BUILD_ID")
export buildString="$releaseString+beta$buildId.$gitHash.$gitUncleanFileCount"

if [ "$gitUncleanFileCount" = "0" ]  
then
  export buildGitRef="#$gitHash"
else
  # buildGitRef will include the basenames of the files which are 
  # dirty relative to the commit identified by gitHash
  gitUncleanFileList=$(basename -a $(git diff --name-only))
  # Wrapping the command with 'echo' collapses newlines in 
  # $gitUncleanFileList to spaces
  export buildGitRef=$(echo "#$gitHash" + changes to: $gitUncleanFileList)
fi

# Android releases require a numeric version code, which must increase
# monotonically over time.
#
# The version code generated below is decimal integer consisting of
# 2 digits each of major, minor and patch version numbers
# followed by 4 digits of build number
# NB GLOBIGNORE is required so that the infix multiplication operator
# isn't handled as a filename glob, also end-of-line characters within
# the bc expression have to be escaped 
GLOBIGNORE="*" export buildAndroidVersionCode=$(bc <<EOF 
  ( $RELEASE_VERSION_MAJOR * 10^8 ) \
+ ( $RELEASE_VERSION_MINOR * 10^6 ) \
+ ( $RELEASE_VERSION_PATCH * 10^4 ) \
+  $BUILD_ID
EOF
)

echo buildString=$buildString
echo buildGitRef=$buildGitRef
echo buildAndroidVersionCode=$buildAndroidVersionCode

# Which fleet should be the deploy target
if [ "$1" = "--fhau-ci-32bit" ]
then
  deploy_fleet=fhau-ci-32bit
  shift
elif [ "$1" = "--fhau-staging" ]
then
  deploy_fleet=fhau-staging
  shift
else
  deploy_fleet=fhau-staging
fi

# Interactive mode to support development
if [ "$1" = "--do-gradle-build" ]
then
  sed -e "s/0.0.0/$buildString/" -i build.gradle
  sed -e "s/9999/$buildAndroidVersionCode/" -i build.gradle
  ./gradlew clean build :android-app:bundleRelease
  echo Gradle products:
  ls -l desktop-app/build/libs
  ls -l android-app/build/outputs/apk/*/*.apk

  #jarsigner -keystore $jkspath \
  #    ./android-app/build/outputs/bundle/release/androidFHAU-$versionBuildString-release.aab \
  #    playstore-upload
  shift
fi

if [ "$1" = "--deploy-balena-beta" ]
then
  sed -e "s/0.0.0/$buildString/" -i deployment/balena/balena.yml
  sed -e "s/%GITREF%/$buildGitRef/" -i deployment/balena/balena.yml
  balena_token=$(cat ~/.balena/token)
  echo Balena token: $balena_token``
  balena login --token $balena_token
  if [ "$1" = "--debug" ]
  then
    balena push --debug --draft --source deployment/balena $deploy_fleet
    shift
  else
    balena push --draft --source deployment/balena $deploy_fleet
  fi
  shift
fi

# By default we leave the files containing version numbers
# modified.
# Leaving them in this state allows us to manually finish off
# builds which fail mid-run.
# This script will reverted them to repository HEAD state before
# version strings are generated next time the script runs.
if [ "$1" = "--restore-versioned-files" ]
then
  git restore build.gradle
  git restore deployment/balena/balena.yml
  shift
fi

if [ ! -z "$*" ]
then
    echo Unexpected command args: $*
fi
