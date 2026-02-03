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

if [ "$1" = "--alph" ]
then
  # shortening 'alpha' to 'alph' is deliberate
  # for real estate reasons
  releaseLevel=alph
  shift
else
  releaseLevel=beta
fi


# This script is run by Jenkins, which is expected to have set
# the three string parameters RELEASE_VERSION_{MAJOR,MINOR,PATCH}
# into environment variables for the run, and is also expected
# to have defined an environment variable BUILD_ID containing
# Jenkins' own run number for this build.

export releaseString=$RELEASE_VERSION_MAJOR.$RELEASE_VERSION_MINOR.$RELEASE_VERSION_PATCH
export gitHash=$(git rev-parse HEAD | cut -c 1-7)
export gitUncleanFileCount=$(git diff --name-only | wc -l)
export buildId=$(printf "%04d" "$BUILD_ID")

export buildString="$releaseString+$releaseLevel$buildId"
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
  export buildString="$buildString.$gitHash.$gitUncleanFileCount"
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

if [ "0" = "0" ] && [ -f ~/.balena/token ]
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
if [ "$1" = "--remove-stale-fakeroot" ]
then
  shift
  if [ -d $balenaFakerootDir ]
  then
    echo Removing stale directory $balenaFakerootDir
    rm -rf $balenaFakerootDir || true
    echo Stale directory removed if present
  else
    echo No stale directory found at $balenaFakerootDir
  fi
else
  echo $balenaFakerootDir already exists.
  echo Please delete it manually or include flag --remove-stale-fakeroot if you want to repeat a prior build.
  exit 1
fi
echo Fakeroot processing done

extra_push_flags=""

# Which device/fleet/git repo should be the deploy target
if [ ! -z "$(echo $1 | grep '192.168')" ]
then
  # IP address of a device, hopefully in local mode
  deploy_fleet=$1
  push_method=balena_cli
  extra_push_flags="--detached --nolive"
  shift
elif [ "$1" = "--maneline-staging" ]
then
  deploy_fleet=maneline-staging
  shift
elif [ "$1" = "--fhau-ci-32bit" ]
then
  deploy_fleet=fhau-ci-32bit
  shift
elif [ "$1" = "--maneline-rpi-any" ]
then
  deploy_fleet=maneline-rpi-any
  shift
else
  deploy_fleet=maneline-staging
  push_method=git
fi
echo Will deploy to $deploy_fleet using $push_method

echo Building deployment root at $balenaFakerootDir
if [ "$push_method" = "git" ]
then
  echo Before clone
  git clone --depth=1 gh_tim_littlefair@git.balena-cloud.com:gh_tim_littlefair/maneline-staging.git $balenaFakerootDir
  echo After clone
  for f_or_d in $(ls -1 $balenaFakerootDir)
  do
    git -C $balenaFakerootDir rm -r $f_or_d
  done
  echo After deletion
  git -C $balenaFakerootDir status
else
  mkdir -p "$balenaFakerootDir"
fi


cp deployment/balena/compose-no-browser/docker-compose.yml $balenaFakerootDir
cp deployment/balena/compose-no-browser/Dockerfile.template $balenaFakerootDir
cp --recursive --force web-app/lua $balenaFakerootDir
cp --recursive --force web-app/run.sh $balenaFakerootDir
cp --recursive --force web-app/web_ui $balenaFakerootDir
cp --recursive --force web-app/lcd_ui $balenaFakerootDir
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

cp --recursive --force desktop-app/build/libs $balenaFakerootDir/jar
cat deployment/balena/compose-no-browser/balena.yml | \
  sed -e "s/0.0.0/$buildString/" | \
  sed -e "s/%GITREF%/$buildGitRef/" \
  > $balenaFakerootDir/balena.yml
echo Files copied to $balenaFakerootDir


if [ "$push_method" = "balena_cli" ]
then
  echo Remaining arguments: $*
  extra_push_flags="$extra_push_flags $*"
  push_cmd="balena push $extra_push_flags --source $balenaFakerootDir gh_tim_littlefair/$deploy_fleet"
  $push_cmd
elif [ ! "$deploy_fleet" = "maneline-staging" ]
  then
  echo "The only push method available for fleets other than maneline-staging is 'balena_cli'"
  exit 1
else
  echo Deploying using git
  echo Fetching and pulling
  git -C $balenaFakerootDir fetch
  git -C $balenaFakerootDir pull
  echo Adding
  for f_or_d in $(ls -1 $balenaFakerootDir)
  do
    git -C $balenaFakerootDir add $f_or_d
  done
  git -C $balenaFakerootDir status
  echo Committing
  git -C $balenaFakerootDir commit -m "Deploying using legacy git method for release $buildString

$buildGitRef"
  echo Pushing
  git -C $balenaFakerootDir push
  echo Push done
fi

if [ ! -z "$*" ]
then
    echo Unexpected command args: $*
fi
