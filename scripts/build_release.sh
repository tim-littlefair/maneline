#! /bin/bash

# This is a quick and dirty script to build and run a release of Maneline
set -e

# Because of problems reported in this message:
# https://forums.balena.io/t/problems-doing-a-balena-push-free-tier/374797/10
# pushing using the balena CLI is sometimes unreliable for this project so the
# legacy git method is supported as an alternative.
push_method=balena_cli

git restore build.gradle
git restore deployment/balena/*/balena.yml

# This script is run by Jenkins, which is expected to have set
# the three string parameters RELEASE_VERSION_{MAJOR,MINOR,PATCH}
# into environment variables for the run, and is also expected
# to have defined an environment variable BUILD_ID containing
# Jenkins' own run number for this build.

export releaseString=$RELEASE_VERSION_MAJOR.$RELEASE_VERSION_MINOR.$RELEASE_VERSION_PATCH
export gitHash=$(git rev-parse HEAD | cut -c 1-7)
export gitUncleanFileCount=$(git diff --name-only | wc -l)
export buildId=$(printf "%04d" "$BUILD_ID")

if [ ! "$buildId" = "9001" ]
then
  # 9001 is an indicator value passed to scripts/version_env.sh
  # to signify a final release build
  export buildString="$releaseString+beta$buildId"
  # buildGitRef will include the basenames of the files which are
  # dirty relative to the commit identified by gitHash
  gitUncleanFileList=$(basename -a $(git diff --name-only))
  # Wrapping the command with 'echo' collapses newlines in 
  # $gitUncleanFileList to spaces
  export buildGitRef=$(echo "#$gitHash" + changes to: $gitUncleanFileList)
  export buildString="$buildString.$gitHash.$gitUncleanFileCount"
elif [ ! "$gitUncleanFileCount" = "0" ]
then
  echo "Can't do a release build with local modified files"
  exit 1
else
  export buildString="$releaseString"
  export buildGitRef="#$gitHash"
  release_tag=release-$releaseString

  echo Validating conditions for release
  set +e
  diff_stats=$(git diff --shortstat $release_tag 2>&1)
  if [ ! -z "$diff_stats" ]
  then
    echo "Can't make a release until label release-$releaseString is applied and matches build tree"
    echo -e "$diff_stats"
    echo Release validation failed
    exit 2
  fi
  set -e
  echo Release validation completed
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
  git restore build.gradle
  echo Gradle products:
  ls -l desktop-app/build/libs
  ls -l android-app/build/outputs/apk/*/*.apk

  #jarsigner -keystore $jkspath \
  #    ./android-app/build/outputs/bundle/release/androidFHAU-$versionBuildString-release.aab \
  #    playstore-upload
  shift
fi

if [ ! "$1" = "--deploy-balena" ]
then
  exit 0
else
  shift
fi

if [ -f ~/.balena/token ]
then
  echo Balena already logged in
elif [ ! -z "$BALENA_TOKEN" ]
then
  set +e
  balena login --token $BALENA_TOKEN
  echo Balena login returned $?
  set -e
else
  echo "No Balena token in filesystem or environment"
  exit 1
fi

balenaFakerootDir=_work/balena_fakeroot-$buildString
if [ ! "$1" = "--no-remove-stale-fakeroot" ]
then
  if [ -d $balenaFakerootDir ]
  then
    echo Removing stale directory $balenaFakerootDir
    rm -rf $balenaFakerootDir || true
    echo Stale directory removed if present
  fi
else
  shift
  if [ -d $balenaFakerootDir ]
  then
    echo $balenaFakerootDir already exists.
    echo Please delete it manually build.
    exit 1
  fi
fi
echo Fakeroot processing done

extra_push_flags=""

# Which device/fleet/git repo should be the deploy target
if [ ! -z "$(echo $1 | grep '192.168')" ]
then
  # IP address of a device, hopefully in local mode
  deploy_fleet=$1
  push_method=balena_cli_local
  extra_push_flags="--detached --nolive"
  shift
elif [ ! -z "$(echo $1 | grep '@git.balena-cloud.com')" ]
then
  deploy_fleet=$1
  push_method=git
  shift
elif [ ! -z "$1" ]
then
  deploy_fleet=$1
  shift
  extra_push_flags=$*
else
  deploy_fleet=maneline-staging
fi
echo Will deploy to $deploy_fleet using $push_method

echo Building deployment root at $balenaFakerootDir
mkdir -p "$balenaFakerootDir"

cp deployment/balena/compose-no-browser/docker-compose.yml $balenaFakerootDir
cp deployment/balena/compose-no-browser/Dockerfile.template $balenaFakerootDir
cp --recursive --force web-app/lua $balenaFakerootDir
cp --recursive --force web-app/run.sh $balenaFakerootDir
cp --recursive --force web-app/web_ui $balenaFakerootDir
cp --recursive --force web-app/lcd_ui $balenaFakerootDir

# The ePaper UI is presently not in use
epaper_enabled=false
if [ "$epaper_enabled" = "true" ]
then
  cp --recursive --force web-app/epaper_ui $balenaFakerootDir
  if [ ! -f _work/E-Paper_code.zip ]
  then
    wget --output-document=_work/E-Paper_code.zip https://files.waveshare.com/upload/7/71/E-Paper_code.zip
  fi
  unzip -qq -o _work/E-Paper_code.zip 'RaspberryPi_JetsonNano/python/lib/**'
  unzip -qq -o _work/E-Paper_code.zip 'RaspberryPi_JetsonNano/python/pic/Font.ttc'
  mv RaspberryPi_JetsonNano/python/lib $balenaFakerootDir/epaper_ui/lib
  mv RaspberryPi_JetsonNano/python/pic $balenaFakerootDir/epaper_ui/pic
  rm -rf RaspberryPi_JetsonNano
fi
# end of disabled ePaper UI

cp --recursive --force desktop-app/build/libs $balenaFakerootDir/jar
cat deployment/balena/compose-no-browser/balena.yml | \
  sed -e "s/0.0.0/$buildString/" | \
  sed -e "s/%GITREF%/$buildGitRef/" \
  > $balenaFakerootDir/balena.yml
echo Files copied to $balenaFakerootDir
sh -c "cd $balenaFakerootDir && zip -r ../maneline-$buildString.zip ."

if [ "$push_method" = "balena_cli" ]
then
  echo Remaining arguments: $*
  extra_push_flags="$extra_push_flags $*"
  push_cmd="balena push $extra_push_flags --source $balenaFakerootDir gh_tim_littlefair/$deploy_fleet"
  $push_cmd
elif [ "$push_method" = "balena_cli_local" ]
then
  echo Remaining arguments: $*
  extra_push_flags="$extra_push_flags $*"
  push_cmd="balena push $extra_push_flags --source $balenaFakerootDir $deploy_fleet"
  $push_cmd
elif [ ! "$deploy_fleet" = "maneline-staging" ]
  then
  echo "The only push method available for fleets other than maneline-staging is 'balena_cli'"
  exit 1
else
  echo Invalid options
  exit 1
fi


