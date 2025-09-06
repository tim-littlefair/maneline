#! /bin/bash

# This is a quick and dirty script to build and run the FHAU command line application
set -e
./gradlew build
cd _work

if [ ! -z "$1" ]
then
  args="$*"
else
  args="--output=maneline-$(date +%F_%H%M).zip"
fi

java -jar ../desktop-app/build/libs/maneline-cli-0.0.0.jar $args
echo maneline-cli exited with status $?

