#!/bin/sh

# The development
devenv_path=$1

if [ -e $devenv_path ]
then
  echo There is either a file or a directory at $devenv_path.
  echo Please check the content of this location and remove
  echo it manually before running this script again.
  exit 1
fi

required_apt_packages="openjdk-17-jdk openjdk-17-doc openjdk-17-dbg openjdk-17-source"
. /etc/os-release
if [ "$ID" = "debian" ]
then
  # Debian 12 contains a package called sdkmanager which
  required_apt_packages="$required_apt_packages sdkmanager"
elif [ "$ID" = "ubuntu" ]
then
  required_apt_packages="$required_apt_packages google-android-cmdline-tools-13.0-installer"
else
  echo Operating system ID "$ID" not recognized
  echo Please ensure Google\'s 'sdkmanager' utility is installed
fi

sudo apt -y update
if [ "$?" != "0" ]
then
  echo sudo not available to user running this script -
  echo ask adminstrator to ensure that the OS is up to date
  echo and that the following packages are installed:
  echo $required_apt_packages
else
  sudo apt -y upgrade
  sudo apt install -y $required_apt_packages
fi

export ANDROID_SDK=$devenv_path/AndroidSdk
export GRADLE_USER_HOME=$devenv_path/GradleUserHome
mkdir $devenv_path
mkdir $ANDROID_SDK
mkdir $GRADLE_USER_HOME

sdkmanager --sdk_root=$ANDROID_SDK --install "platforms;android-34.ext12"
sdkmanager --sdk_root=$ANDROID_SDK --install "sources;android-34"
sdkmanager --sdk_root=$ANDROID_SDK --install "build-tools;34.0.0"
sdkmanager --sdk_root=$ANDROID_SDK --install "system-images;android-34;aosp_atd;x86_64"
sdkmanager --sdk_root=$ANDROID_SDK --update



