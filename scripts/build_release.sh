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
  shift

  if [ -f ~/.balena/token ]
  then
     echo Balena already logged in
  elif [ ! -z "$BALENA_TOKEN" ]
  then
     balena login --token $BALENA_TOKEN
  else
     echo "No Balena token in filesystem or environment"
     exit 1
  fi

  balenaFakerootDir=_work/balena_fakeroot-$buildString
  if [ -e  "$balenaFakerootDir" ]
  then
    echo $balenaFakerootDir already exists.
    echo Please delete it manually if you want to repeat a prior build.
    exit 1
  fi

  mkdir -p $balenaFakerootDir
  ln -s ../compose-no-browser/* $balenaFakerootDir
  ln -s ../platform-layer/* $balenaFakerootDir
  ln -s ../platform-layer/* $balenaFakerootDir
  ln -s ../../../web-app/web_ui $balenaFakerootDir
  ln -s ../../../desktop-app/build/libs $balenaFakerootDir/jar
  rm $balenaFakerootDir/balena.yml
  cat ../compose-no-browser/balena.yml | \
    sed -e "s/0.0.0/$buildString/" | \
    sed -e "s/%GITREF%/$buildGitRef/" \
    > $balenaFakerootDir/balena.yml

  # Which fleet should be the deploy target
  if [ "$1" = "--fhau-ci-32bit" ]
  then
    deploy_fleet=fhau-ci-32bit
    shift
  elif [ "$1" = "--fhau-staging" ]
  then
    deploy_fleet=fhau-staging
    shift
  elif [ "$1" = "--maneline-rpi-any" ]
  then
    deploy_fleet=maneline-rpi-any
    shift
  else
    deploy_fleet=fhau-staging
  fi

  if [ "$1" = "--debug" ]
  then
    push_cmd="balena push --debug --draft --source $balenaFakerootDir $deploy_fleet"
    shift
  else
    push_cmd="balena push --draft --source $balenaFakerootDir $deploy_fleet"
  fi
  echo $push_cmd
  $push_cmd
  shift
fi

# By default, in dev/interactive mode we leave $balenaFakerootDir
# on the filesystem to allow tweaks and re-runs.
# The following flag overrides this and deletes the
# directory, and will be used in successful CI runs.
if [ "$1" = "--rm-balena-fakeroot" ]
then
  rm -rf $balendaFakerootDir
  shift
fi

if [ ! -z "$*" ]
then
    echo Unexpected command args: $*
fi
