#! /bin/bash

# This is a quick and dirty script to build and run the FHAU command line application
set -e
./gradlew build
cd _work
java -jar ../desktop-app/build/libs/desktopFHAUcli-0.0.0.jar $*
