#!/bin/sh

# The purpose of this script is to build a composite SDK for
# FHAU development, which consists of the following components:
# + a vendor Java Development Kit
# + a vendor Android SDK
# + the user preferences directory where the Android SDK
#   stores its settings
# + a gradle user home directory where gradle packages
#   are cached after first use.

# The FHAU SDK could be anywhere but it is recommended
# it should sit as a sibling directory alongside the
# root of the Git repository so that
# a) there is no danger of accidentally checking the 
# SDK in; and
# b) SDK tools can be run manually from a shell in the
# root of the repository using command lines based on
# easy to remember relative paths.
# This script enforces that recommendation by accepting
# the path relative to the parent of the Git repo as
# its parameter.

if [ ! -z "$1" ]
then
    SDK_NAME=$1
    echo SDK name from command line argument: $SDK_NAME
elif [ ! -z "$SDK_NAME" ]
then
    SDK_NAME=$1
    echo SDK name from environment: $SDK_NAME
    # Environment variable is intended for use in a CI
    # job where a new SDK is built from scratch
    # before building the FHAU project itself
else
    echo No value found for SDK_NAME
    exit 1
fi

# We expect the run to start in the root directory
# of the Git repository
repo_dir=$(pwd)

fhau_sdk_reldir=../$SDK_NAME

# The script expects that when it runs, the 
# development environment directory will not exist...
if [ -e $fhau_sdk_reldir ]
then
  echo There is either a file or a directory at $fhau_sdk_reldir.
  echo Please check the content of this location and remove
  echo it manually before running this script again.
  exit 2
fi

# ... but its parent will
mkdir $fhau_sdk_reldir
if [ ! "$?" = "0" ]
then
  echo Unable to create devenv at $fhau_sdk_reldir
  exit 3
fi
cd $fhau_sdk_reldir
fhau_sdk_absdir=$(pwd)
echo Absolute path to SDK is $fhau_sdk_absdir

# ... and a download cache might or might not
cache_reldir=../cache
if [ ! -d $cache_reldir ]
then
  mkdir $cache_reldir
  if [ ! "$?" = "0" ]
  then
    echo Unable to create cache
    exit 4
  fi
fi
cd $cache_reldir
cache_absdir=$(pwd)

# For the moment we only support Linux/x64
# but we use variables which could be varied
# at some time in the future for macOS, Windows
# Linux/ARM etc

# Development presently standardises on JDK 21, using 
# Oracle's openjdk archive at
# https://jdk.java.net/archive/
# It also uses Android command line tools,
# URLs for these are available at
# https://developer.android.com/studio#command-line-tools-only

osname=$(uname -s)
if [ "$osname" = "Linux" ]
then
  jdk_url=https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_linux-x64_bin.tar.gz
  android_cltools_url=https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
elif [ "$osname" = "Darwin" ]
then
  jdk_url=https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_macos-x64_bin.tar.gz
  android_cltools_url=https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip
fi

android_cltools_file=$(basename $(echo $android_cltools_url | sed -e s^https:/^^))
jdk_file=$(basename $(echo $jdk_url | sed -e s^https:/^^))

if [ ! -e $jdk_file ]
then
  echo Downloading $jdk_url
  curl $jdk_url -o $jdk_file
fi

if [ ! -e $android_cltools_file ]
then
  echo Downloading $android_cltools_url
  curl $android_cltools_url -o $android_cltools_file
fi

cd $fhau_sdk_absdir
pwd

echo Unpacking $jdk_file
tar xzvf $cache_absdir/$jdk_file

echo Unpacking $android_cltools_file
mkdir android-sdk
cd android-sdk
unzip $cache_absdir/$android_cltools_file
mv cmdline-tools latest
mkdir cmdline-tools
mv latest cmdline-tools/latest

cd $fhau_sdk_absdir

if [ -d $fhau_sdk_absdir/jdk-21.0.2 ]
then
  export JAVA_HOME=$fhau_sdk_absdir/jdk-21.0.2
elif [ -d $fhau_sdk_absdir/jdk-21.0.2.jdk/Contents/Home ]
then
  export JAVA_HOME=$fhau_sdk_absdir/jdk-21.0.2.jdk/Contents/Home
else
  echo Could not find appropriate JAVA_HOME
  exit 5
fi
ANDROID_HOME=$fhau_sdk_absdir/android-sdk

cat > $fhau_sdk_absdir/fhau_sdk_vars.sh <<+

JAVA_HOME=$JAVA_HOME
ANDROID_HOME=$ANDROID_HOME
ANDROID_USER_HOME=$ANDROID_HOME
GRADLE_USER_HOME=$fhau_sdk_absdir/gradle-user-home
GRADLE_LOCAL_JAVA_HOME=$JAVA_HOME
PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$JAVA_HOME/bin:$PATH

export JAVA_HOME ANDROID_HOME ANDROID_USER_HOME GRADLE_USER_HOME GRADLE_LOCAL_JAVA_HOME PATH

+

# We need to overwrite a non-version-controlled file in the root
# directory of the repository to ensure the SDK is found by Gradle
cat > $repo_dir/local.properties <<+

# local.properties file overwritten by $0 on $(date --iso-8601)
sdk.dir=$fhau_sdk_absdir

+

. $fhau_sdk_absdir/fhau_sdk_vars.sh

# I haven't yet found a way of caching the Android SDK download packages
# Also, Gradle seems to need more than one version of build-tools
# to complete the first build
yes | sdkmanager --install \
  "build-tools;34.0.0" \
  "build-tools;35.0.0" \
  "build-tools;35.0.1" \
  "platform-tools" \
  "emulator" \
  "sources;android-35" \
  "platforms;android-35" \
  "system-images;android-35;aosp_atd;x86_64"

yes | sdkmanager --update

yes | sdkmanager --licenses

sdkmanager --list_installed

exit 0

