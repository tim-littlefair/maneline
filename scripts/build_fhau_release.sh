#! /bin/bash

# This is a quick and dirty script to build and run a FHAU release
set -e

# This script is run by Jenkins, which is expected to have set
# the three string parameters RELEASE_VERSION_{MAJOR,MINOR,PATCH}
# into environment variables for the run, and is also expected
# to have defined an environment variable BUILD_ID containing
# Jenkins' own run number for this build.

export versionReleaseString=$RELEASE_VERSION_MAJOR.$RELEASE_VERSION_MINOR.$RELEASE_VERSION_PATCH
export versionJenkinsBuildId=$BUILD_ID
export versionGitHash=$(git rev-parse HEAD | cut -c 1-7)
export versionBuildString=$versionReleaseString-$versionGitHash-$versionJenkinsBuildId
# Version code is a decimal integer consisting of 2 digits each of major, minor and patch
# version numbers followed by 4 digits of build number
export versionCode=$(echo "
  0
  $RELEASE_VERSION_MAJOR +
  100 * $RELEASE_VERSION_MINOR +
  100 * $RELEASE_VERSION_PATCH +
  10000 * $BUILD_ID + p
  " | dc )

env | grep version

sed -e "s/0.0.0/$versionBuildString/" -i build.gradle
sed -e "s/9999/$versionCode/" -i build.gradle

