#! /bin/bash

# This is a quick and dirty script to make it easier to create quick and
# dirty test/debug cases for classes in the directory
# lib/src/main/java/net/heretical_camelid/fhau/lib by adding a
# public static void main(String[] args) to those classes

# Exit without running 'java' if 'javac' fails (bash behaviour)
set -e

debug_echo() {
  # Comment the return statement to reveal messages posted via this function
  return
  echo $*
}

debug_echo 1
if [ ! -z "$1" ]
then
  # Run a single class
  CLASSES="$1"
else
  # Run all classes for which tests (including empty placeholders) exist
  CLASSES=""

  CLASSES+=" PresetRecord"
  CLASSES+=" PresetInfo"
  CLASSES+=" ILoggingAgent"
  CLASSES+=" IAmpProvider"
  CLASSES+=" MessageConstants_LT40S"
  CLASSES+=" MessageProtocol_LT40S"
  CLASSES+=" MessageProtocol_NoDev"
  CLASSES+=" RawProtobufUtilities"
  CLASSES+=" TransportDelegateBase"
  CLASSES+=" MessageProtocolBase"
  CLASSES+=" DefaultLoggingAgent"
  CLASSES+=" SimulatorTransportDelegate"
  CLASSES+=" DeviceDelegateLT40S"
  CLASSES+=" SimulatorAmpProvider"
fi
debug_echo 2 $CLASSES

for c in $CLASSES
do
  debug_echo 3 $c
  rm _work/net/heretical_camelid/fhau/lib/$c.class || true
  debug_echo 4 $c
done

for c in $CLASSES
do
  debug_echo 5 $c
  javac -cp ./_work -d ./_work lib/src/main/java/net/heretical_camelid/fhau/lib/$c.java
  debug_echo 6 $c
  echo "Running $c.main()"
  java -cp ./_work -ea  net.heretical_camelid.fhau.lib.$c
  debug_echo 7 $c
done
