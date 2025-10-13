#!/bin/sh

start_dir=$(pwd)

# The purpose of this script is to build a composite SDK for
# Maneline development, which consists of the following components:
# + a vendor Java Development Kit
# + a vendor Android SDK
# + the user preferences directory where the Android SDK
#   stores its settings
# + a gradle user home directory where gradle packages
#   are cached after first use.
# An installation of the Balena command line interface (CLI)
# An installation of Lua

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
cd $sdk_absdir

# Linux/x64 is the primary build platform

# This script may also be able to create a viable
# command line build environment on macOS but testing  
# on this platform will be infrequent and will be 
# limited to hardware available to the Maneline
# developer(s), which presently consists of:
# one MBP-mid2012 running macOS 10.15 Catalina and 
# one MBP-late2011 running whatever macOS version is 
# currently supported by OpenCore Legacy Patcher
# (macOS 15.5 as at August 2025)

# It may be possible to create a viable command line
# build environment on Windows (either in the native 
# command line or in WSL), but this script does not
# and will not attempt to do this.

# Development presently standardises on JDK 21, using
# Oracle's openjdk archive at
# https://jdk.java.net/archive/
# under the GPLv2+classpath exception license conditions.

# It also uses Android command line tools,
# URLs for these are available at
# https://developer.android.com/studio#command-line-tools-only

download_and_unpack () {
  _url=$1
  _unpack_cmd=$2

  _file_bn=$(basename $(echo $_url | sed -e s^https:/^^))
  if [ ! -e $cache_absdir/$_file_bn ]
  then
    echo Downloading $_url
    curl -L $_url -o $cache_absdir/$_file_bn
  fi
  echo Unpacking $cache_absdir/$_file_bn
  $_unpack_cmd $cache_absdir/$_file_bn
}

set_java_home () {
  _jdkver=$1

  if [ -d $sdk_absdir/$_jdkver ]
  then
    # Linux location
    export JAVA_HOME=$sdk_absdir/$_jdkver
  elif [ -d $sdk_absdir/$_jdkver.jdk/Contents/Home ]
  then
    # macOS location
    export JAVA_HOME=$sdk_absdir/$_jdkver.jdk/Contents/Home
  else
    echo Could not find appropriate JAVA_HOME
    exit 5
  fi
}

build_lua () {
  _dir=$1
  _make_target=$2

  echo Building $_dir
  cd $_dir
  make $lua_make_target
  mkdir bin
  mv src/lua src/luac bin
  cd ..
  echo $_dir built
}

build_luarocks () {
  _dir=$1
  _luadir=$sdk_absdir/$2

  echo Building $_dir for $luadir
  cd $_dir
  ./configure --prefix=$_luadir \
    --with-lua=$_luadir \
    --with-lua-bin=$_luadir/bin \
    --with-lua-include=$_luadir/src \
    --with-lua-lib=$_luadir/src
  make
  make install
  make clean
  for p in \
    luasec luasocket lualogging luafilesystem lua-cjson cqueues \
    mimetypes lzlib pegasus
  do
    $_luadir/bin/luarocks install $p
  done
  cd ..
  echo $_dir for $luadir built
}

osname=$(uname -s)
if [ "$osname" = "Linux" ]
then
  jdk21_url=https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_linux-x64_bin.tar.gz
  jdk25_url=https://download.java.net/java/GA/jdk25/bd75d5f9689641da8e1daabeccb5528b/36/GPL/openjdk-25_linux-x64_bin.tar.gz
  balena_cli_url=https://github.com/balena-io/balena-cli/releases/download/v22.4.9/balena-cli-v22.4.9-linux-x64-standalone.tar.gz
  android_cltools_url=https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  lua_make_target=linux
elif [ "$osname" = "Darwin" ]
then
  # For now, Intel binaries are preferred to those for Apple Silicon
  jdk21_url=https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_macos-x64_bin.tar.gz
  jdk25_url=https://download.java.net/java/GA/jdk25/bd75d5f9689641da8e1daabeccb5528b/36/GPL/openjdk-25_macos-x64_bin.tar.gz
  balena_cli_url=https://github.com/balena-io/balena-cli/releases/download/v22.4.9/balena-cli-v22.4.9-macOS-x64-standalone.tar.gz
  android_cltools_url=https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip
  lua_make_target=macosx
fi

# Lua is downloaded as source - same on both architectures
# (but in either case C/C++/autoconf build tools will be required)
lua51_url=https://www.lua.org/ftp/lua-5.1.5.tar.gz
lua54_url=https://www.lua.org/ftp/lua-5.4.8.tar.gz
luarocks_url=https://luarocks.org/releases/luarocks-3.12.2.tar.gz

download_and_unpack $jdk21_url "tar xzvf"
download_and_unpack $jdk25_url "tar xzvf"
set_java_home jdk-21.0.2

download_and_unpack $lua51_url "tar xzvf"
download_and_unpack $lua54_url "tar xzvf"
download_and_unpack $luarocks_url "tar xzvf"
build_lua lua-5.1.5 linux
build_lua lua-5.4.8 all
build_luarocks luarocks-3.12.2 lua-5.1.5
build_luarocks luarocks-3.12.2 lua-5.4.8

export LUA_HOME=$sdk_absdir/lua-5.1.5
export LUA_PATH="$LUA_HOME/share/lua/5.1/?.lua;$LUA_HOME/share/lua/5.1/?/init.lua;;"
export LUA_CPATH="$LUA_HOME/lib/lua/5.1/?.so;;"

download_and_unpack $balena_cli_url "tar xzvf"
balena_version=$(./balena/bin/balena --version)
mv balena balena-$balena_version
BALENA_HOME=$sdk_absdir/balena-$balena_version

mkdir -p Android/cmdline-tools
download_and_unpack $android_cltools_url "unzip -d Android/cmdline-tools"
mv Android/cmdline-tools/cmdline-tools Android/cmdline-tools/latest
ANDROID_USER_HOME=$(pwd)/Android

cat > $sdk_absdir/sdk-vars.sh <<+

JAVA_HOME=$JAVA_HOME
ANDROID_USER_HOME=$ANDROID_USER_HOME
ANDROID_HOME=$ANDROID_USER_HOME
GRADLE_USER_HOME=$sdk_absdir/gradle-user-home
GRADLE_LOCAL_JAVA_HOME=$JAVA_HOME
BALENA_HOME=$BALENA_HOME
LUA_HOME=$LUA_HOME

export JAVA_HOME LUA_HOME BALENA_HOME
export ANDROID_HOME ANDROID_USER_HOME GRADLE_USER_HOME GRADLE_LOCAL_JAVA_HOME

# Note that the custom entries are added to PATH in reverse order of priority
PATH=\$BALENA_HOME/bin:\$PATH
PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH
PATH=\$LUA_HOME/bin:\$PATH
PATH=\$JAVA_HOME/bin:\$PATH
export PATH

LUA_PATH="$LUA_PATH"
LUA_CPATH="$LUA_CPATH"
export LUA_PATH LUA_CPATH
+

. $sdk_absdir/sdk-vars.sh

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

cd $start_dir
if [ -x ./gradlew ]
then
  time ./gradlew clean build
fi

which java
which balena
which lua
which luarocks
which sdkmanager

exit 0

