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

class=net.heretical_camelid.maneline.$1
class_java_src=$(ls */src/main/java/$(echo $class | sed -e 's^\.^/^g').java)
ls -l $class_java_src

javac -proc:full -cp ./desktop-app/build/libs/maneline-cli-0.0.0.jar -d ./_work $class_java_src
jar_file=./desktop-app/build/libs/maneline-cli-0.0.0.jar
java -cp ./_work:$jar_file -Djava.library.path=../tl-bluetooth-cli/target/native/linux/x86_64 $class
