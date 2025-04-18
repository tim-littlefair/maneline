#! /bin/bash

# This is a quick and dirty script to build and run a FHAU release
set -e

# This script is run by Jenkins, which is expected to have set
# the three string parameters RELEASE_VERSION_{MAJOR,MINOR,PATCH}
# into environment variables for the run, and is also expected
# to have defined an environment variable BUILD_ID containing
# Jenkins' own run number for this build.

versionReleaseString=$RELEASE_VERSION_MAJOR.$RELEASE_VERSION_MINOR.$RELEASE_VERSION_PATCH
versionJenkinsBuildId=$BUILD_ID
versionGitHash=$(git rev-parse HEAD | cut -c 1-7)

echo X
env | grep version
echo Y

echo Build not implemented yet

exit 1