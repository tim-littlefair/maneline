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
    echo SDK name from environment: $SDK_NAME
    # Environment variable is intended for use in a CI
    # job where a new SDK is built from scratch
    # before building the FHAU project itself
else
    echo No value found for SDK name
    exit 1
fi

# We expect the run to start in the root directory
# of the Git repository
repo_dir=$(pwd)

sdk_reldir=../$SDK_NAME

# The script expects that when it runs, the 
# development environment directory will not exist...
if [ -e $sdk_reldir ]
then
  echo There is either a file or a directory at $sdk_reldir.
  echo Please check the content of this location and remove
  echo it manually before running this script again.
  exit 2
fi

# ... but its parent will
mkdir $sdk_reldir
if [ ! "$?" = "0" ]
then
  echo Unable to create devenv at $sdk_reldir
  exit 3
fi
cd $sdk_reldir
sdk_absdir=$(pwd)
echo Absolute path to SDK is $sdk_absdir

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

# Linux/x64 is the primary build platform for FHAU

# This script may also be able to create a viable
# command line build environment on macOS but testing  
# on this platform will be infrequent and will be 
# limited to hardware available to the FHAU 
# developer(s), which presently consists of:
# one MBP-mid2012 running macOS 10.15 Catalina and 
# one MBP-late2011 running whatever macOS version is 
# currently supported by OpenCore Legacy Patcher
# (macOS 15.5 as at August 2025)

# It may be possible to create a viable command line
# build environment on Windows (either in the native 
# command line or in WSL), but this script does not
# and will not attempt to do this.
# Note that the deployment/balena module build scripts
# depend on symbolic links stored in git - as Git on 
# Windows filesystems cannot create symbolic links 
# this part of the project will definitely not 
# be buildable.

# Development presently standardises on JDK 21, using 
# Oracle's openjdk archive at
# https://jdk.java.net/archive/
# It also uses Android command line tools,
# URLs for these are available at
# https://developer.android.com/studio#command-line-tools-only

osname=$(uname -s)
if [ "$osname" = "Linux" ]
then
  jdk21_url=https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_linux-x64_bin.tar.gz
  jdk25_url=https://download.java.net/java/GA/jdk25/bd75d5f9689641da8e1daabeccb5528b/36/GPL/openjdk-25_linux-x64_bin.tar.gz
  android_cltools_url=https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  balena_cli_url=https://github.com/balena-io/balena-cli/releases/download/v22.4.4/balena-cli-v22.4.4-linux-x64-standalone.tar.gz
elif [ "$osname" = "Darwin" ]
then
  # For now, Intel binaries are preferred to those for Apple Silicon
  jdk21_url=https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_macos-x64_bin.tar.gz
  jdk25_url=https://download.java.net/java/GA/jdk25/bd75d5f9689641da8e1daabeccb5528b/36/GPL/openjdk-25_macos-x64_bin.tar.gz
  android_cltools_url=https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip
  balena_cli_url=https://github.com/balena-io/balena-cli/releases/download/v22.4.4/balena-cli-v22.4.4-macOS-x64-standalone.tar.gz
fi

# Lua is downloaded as source - same on both architectures
# (but in either case C/C++/autoconf build tools will be required)
lua51_url=https://www.lua.org/ftp/lua-5.1.5.tar.gz
lua54_url=https://www.lua.org/ftp/lua-5.4.8.tar.gz
luarocks_url=https://luarocks.org/releases/luarocks-3.12.2.tar.gz

android_cltools_file=$(basename $(echo $android_cltools_url | sed -e s^https:/^^))
jdk21_file=$(basename $(echo $jdk21_url | sed -e s^https:/^^))
balena_cli_file=$(basename $(echo $balena_cli_url | sed -e s^https:/^^))

cd $sdk_absdir

if [ ! -e $cache_absdir/$balena_cli_file ]
then
  echo Downloading $balena_cli_url
  curl -L $balena_cli_url -o $cache_absdir/$balena_cli_file
fi
echo unpacking $balena_cli_file
tar xzvf $cache_absdir/$balena_cli_file
balena_version=$(
  echo $balena_cli_file |
  sed -e 's/balena-cli-//' |
  sed -e 's/-x64-standalone.tar.gz//'
)
mv balena balena-$balena_version
BALENA_HOME=$sdk_absdir/balena-$balena_version

if [ ! -e $cache_absdir/$jdk21_file ]
then
  echo Downloading $jdk21_url
  curl $jdk21_url -o $jdk21_file
fi
echo Unpacking $jdk21_file
tar xzvf $cache_absdir/$jdk21_file
if [ -d $sdk_absdir/jdk-21.0.2 ]
then
  # Linux location
  export JAVA_HOME=$sdk_absdir/jdk-21.0.2
elif [ -d $sdk_absdir/jdk-21.0.2.jdk/Contents/Home ]
then
  # macOS location
  export JAVA_HOME=$sdk_absdir/jdk-21.0.2.jdk/Contents/Home
else
  echo Could not find appropriate JAVA_HOME
  exit 5
fi

if [ ! -e $cache_absdir/$android_cltools_file ]
then
  echo Downloading $android_cltools_url
  curl $android_cltools_url -o $android_cltools_file
fi
echo Unpacking $android_cltools_file
mkdir $sdk_absdir/Android
cd $sdk_absdir/Android
unzip $cache_absdir/$android_cltools_file
mv cmdline-tools latest
mkdir cmdline-tools
mv latest cmdline-tools/latest
cd $sdk_absdir
ANDROID_USER_HOME=$sdk_absdir/Android

cat > $sdk_absdir/maneline-sdk-vars.sh <<+

JAVA_HOME=$JAVA_HOME
ANDROID_USER_HOME=$ANDROID_USER_HOME
ANDROID_HOME=$ANDROID_USER_HOME
GRADLE_USER_HOME=$sdk_absdir/gradle-user-home
GRADLE_LOCAL_JAVA_HOME=$JAVA_HOME
BALENA_HOME=$BALENA_HOME

PATH=\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$BALENA_HOME/bin:\$PATH

export ANDROID_HOME ANDROID_USER_HOME GRADLE_USER_HOME GRADLE_LOCAL_JAVA_HOME
export JAVA_HOME BALENA_HOME PATH

+

. $sdk_absdir/maneline-sdk-vars.sh

# We need to overwrite a non-version-controlled file in the root
# directory of the repository to ensure the SDK is found by Gradle
cat > $repo_dir/local.properties <<+
# local.properties file overwritten by $0 on $(date --iso-8601)
sdk.dir=$sdk_absdir/Android
+

sdkmanager=$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager

# I haven't yet found a way of caching the Android SDK download packages
# Also, Gradle seems to need more than one version of build-tools
# to complete the first build
yes | $sdkmanager --install \
  "build-tools;35.0.0" \
  "build-tools;36.0.0" \
  "platform-tools" \
  "emulator" \
  "sources;android-36" \
  "platforms;android-36" \
  "system-images;android-35;aosp_atd;x86_64"

yes | $sdkmanager --update
yes | $sdkmanager --licenses > licenses.txt
$sdkmanager --list_installed

exit 0

