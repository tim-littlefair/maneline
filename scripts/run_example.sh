#! /bin/bash

# This is a quick and dirty script to build and run the FHAU command line application
set -e
./gradlew build
cd _work

example_class=$1
shift

if [ ! -z "$1" ]
then
  args="$*"
else
  args=
fi

# For example HelloTinyB, we presently need the native libraries to be installed into the filesystem
# On TL's development machine this hs done, these files are installed to /usr/local/lib, and the runtime
# Java path needs tweaking to include this.
# TBD: find a way of packaging them into the JAR
LD_LIBRARY_PATH=../desktop-app/libs/linux-x86-64:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

java -cp ../desktop-app/build/libs/desktopFHAUcli-0.0.0.jar net.heretical_camelid.fhau.desktop_app.$example_class $args
echo $example_class exited with status $?

