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

export CLASSPATH
echo $CLASSPATH
sudo java -cp $CLASSPATH -ea  net.heretical_camelid.fhau.desktop_app.CommandLineInterface $*

