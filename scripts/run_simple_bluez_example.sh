#! /bin/bash

# This is a quick and dirty script to build and run the FHAU command line application
set -e
./gradlew build
cd _work

if [ ! -z "$1" ]
then
  args="$*"
else
  args=
fi

sudo java -cp ../desktop-app/build/libs/desktopFHAUcli-0.0.0.jar net.heretical_camelid.fhau.desktop_app.SimpleBluez $args
echo SimpleBluez exited with status $?

