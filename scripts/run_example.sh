#! /bin/bash

# This is a quick and dirty script to build and run example code from libaries
# which are under evaluation for integration into FHAU.
# The two examples under evaluation at the moment both relate to BLE capabilities,
# and are defined in the SimpleBluez.java and HelloTinyB.java files which
# have been minimally adapted from their upstream source to fit into the
# net.heretical_camelid.fhau.desktop app package.

# See the note later in this file about the complications introduced by the
# HelloTinyB.java example.

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

# Example HelloTinyB.java is copied from a BLE library called tinyb, published here:
# https://github.com/intel-iot-core/tinyb
# This library is now in read-only archive state on GitHub and it is clear that
# the original project owners (presumably part of Intel Corp), will not be
# continuing development.
# At one time, a prebuilt copy of this library was available from one of
# the Maven repositories hosted on BinTray, but this host no longer exists,
# and there does not appear to be a viable prebuilt copy anywhere.
# I've forked the repository to:
# https://github.com/tim-littlefair/tl-tinyb
# and created a new primary branch in which I've updated the build
# scripts to work on my Ubuntu 24.04 build environment, and to
# check in the .jar file and the two .so files required to expore this further

# Uncomment the following 3 lines to unpack the releaseable .zip file from
# a checkout of tl-tinyb as a sibling to the current checkout
#cd ../desktop-app/libs
#unzip -o ../../../tl-tinyb/precompiled-release-artifacts/tinyb-0.5.1.tl250617.zip
#cd ../../_work

# For example HelloTinyB, we presently need LD_LIBRARY_PATH to include the directory
# containing (tl-)tinyb native libraries.
LD_LIBRARY_PATH=$LD_LIBRARY_PATH:../desktop-app/libs/linux-x86_64
export LD_LIBRARY_PATH

java -cp ../desktop-app/build/libs/desktopFHAUcli-0.0.0.jar net.heretical_camelid.fhau.desktop_app.$example_class $args
echo $example_class exited with status $?

