#! /bin/bash

# This is a quick and dirty script to build and run a FHAU release
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
export gitUncleanFileCount=$(git diff --shortstat)
export gitUncleanFileList=$(git diff --name-status)
export buildId=$(printf "%04d" "$BUILD_ID")
export buildString="$releaseString-beta$buildId"
export buildGitRef="#$gitHash + $gitUncleanFileCount"
# Android release require a numeric version code, which must increase
# monotonically over time.
#
# The version code generated below is decimal integer consisting of
# 2 digits each of major, minor and patch version numbers
# followed by 4 digits of build number
#
# NB
# dc consumes REVERSE POLISH - the operators in the expression below
# are NOT INFIX, they pop two values from the stack, apply to those values
# and push the result back onto the stack
export buildCode=$(echo "
  0
  $RELEASE_VERSION_MAJOR +
  100 * $RELEASE_VERSION_MINOR +
  100 * $RELEASE_VERSION_PATCH +
  10000 * $BUILD_ID + p
  " | dc )

env | grep -e "^build"

# Interactive mode to support development
if [ "$1" = "--do-gradle-build" ]
then
  sed -e "s/0.0.0/$buildString/" -i build.gradle
  sed -e "s/9999/$buildCode/" -i build.gradle
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
  balena login --token $BALENA_TOKEN
  sed -e "s/0.0.0/$buildString/" -i deployment/balena/balena.yml
  sed -e "s/%GITREF%/$buildGitRef/" -i deployment/balena/balena.yml
  shift
  if [ "$1" = "--debug"]
  then
    balena push --debug --draft --source deployment/balena fhau-staging
    shift
  else
    balena push --draft --source deployment/balena fhau-staging
  fi
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
fi

