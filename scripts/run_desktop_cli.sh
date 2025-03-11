#! /bin/bash

# This is a quick and dirty script to run the application for
# which the main class is
# desktop-app/src/main/java/net/heretical_camelid/fhau/desktop-app/CommandLineInterface.java

# Exit without running 'java' if 'javac' fails (bash behaviour)
set -e

debug_echo() {
  # Comment the return statement to reveal messages posted via this function
  #return
  echo $*
}

CLASSPATH="desktop-app/build/libs/desktop-app.jar"
CLASSPATH="$CLASSPATH:lib/build/libs/lib.jar"
CLASSPATH="$CLASSPATH:./desktop-app/build/resources"
CLASSPATH="$CLASSPATH:./_work/dist/desktop-app/lib/hid4java-0.8.0.jar"
CLASSPATH="$CLASSPATH:./_work/dist/desktop-app/lib/jna-5.8.0.jar"

export CLASSPATH
echo $CLASSPATH
java -cp $CLASSPATH -ea  net.heretical_camelid.fhau.desktop_app.CommandLineInterface $*

debug_echo 7 $c
debug_echo HW
exit 1


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
  CLASSES+=" RawProtobufUtilities"
  CLASSES+=" TransportDelegateBase"
  CLASSES+=" MessageProtocolBase"
  CLASSES+=" MessageConstants_LT40S"
  CLASSES+=" MessageProtocol_LT40S"
  CLASSES+=" MessageProtocol_NoDev"
  CLASSES+=" DefaultLoggingAgent"
  CLASSES+=" SimulatorTransportDelegate"
  CLASSES+=" DeviceDelegateLT40S"
  CLASSES+=" SimulatorAmpProvider"
fi
debug_echo 2 $CLASSES

for c in $CLASSES
do
  debug_echo 3 $c
  rm _work/net/heretical_camelid/fhau/lib/$c.class && true
  debug_echo 4 $c
done

for c in $CLASSES
do
  debug_echo 5 $c
  ls -l ./lib/src/main/java/net/heretical_camelid/fhau/lib/$c.java
  javac -cp ./_work -d ./_work lib/src/main/java/net/heretical_camelid/fhau/lib/$c.java
  ls -l ./_work/net/heretical_camelid/fhau/lib/$c.class
  debug_echo 6 $c
  echo "Running $c.main()"
  java -cp ./_work -ea  net.heretical_camelid.fhau.lib.$c
  debug_echo 7 $c
done
