#!/bin/sh


# The development environment can be anywhere but it 
# is recommended it should sit as a sibling directory 
# alongside the root of the Git repository so that
# a) there is no danger of accidentally checking the 
# development environment in; and
# b) development environment tools can be run manually
# from a shell in the root of the repository with 
# simple consistent command lines.

devenv_reldir=$1

# The script expects that when it runs, the 
# development environment directory will not exist...
if [ -e $devenv_reldir ]
then
  echo There is either a file or a directory at $devenv_reldir.
  echo Please check the content of this location and remove
  echo it manually before running this script again.
  # exit 1
fi

# ... but its parent will
mkdir $devenv_reldir
if [ ! "$?" = "0" ]
then
  echo Unable to create devenv at $devenv_reldir
  # exit 1
fi

# ... and a download cache might or might not
cd $devenv_reldir
devenv_absdir=$(pwd)
cache_reldir=../cache
if [ ! -d $cache_reldir ]
then
  mkdir $cache_reldir
  if [ ! "$?" = "0" ]
  then
    echo Unable to create cache
    exit 2
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
jdk_url=https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_linux-x64_bin.tar.gz
jdk_file=$(basename $(echo $jdk_url | sed -e s^https:/^^))

# It also uses Android command line tools, 
# URLs for these are available at 
# https://developer.android.com/studio#command-line-tools-only
android_cltools_url=https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
android_cltools_file=$(basename $(echo $android_cltools_url | sed -e s^https:/^^))

if [ ! -e $jdk_file ]
then
  echo Downloading $jdk_url
  wget --progress=dot:giga $jdk_url
fi

if [ ! -e $android_cltools_file ]
then
  echo Downloading $android_cltools_url
  wget --progress=dot:giga $android_cltools_url
fi

cd $devenv_absdir
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

export JAVA_HOME=$devenv_absdir/jdk-21.0.2
yes | cmdline-tools/latest/bin/sdkmanager --install \
  "build-tools;35.0.1" \
  "build-tools;34.0.0" \
  "platform-tools" \
  "emulator" \
  "sources;android-35" \
  "platforms;android-35" \
  "system-images;android-35;aosp_atd;x86_64"

yes | cmdline-tools/latest/bin/sdkmanager --update

cmdline-tools/latest/bin/sdkmanager --list_installed

exit 0

