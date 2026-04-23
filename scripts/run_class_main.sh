#! /bin/bash

# This is a script to make it easier to create quick and
# dirty test/debug cases for classes in and under the directory
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

# Remove all stale .class files created by prior invocations of this script
# (not just the one we are trying to test in this run).
rm -rf ./_work/net

class=net.heretical_camelid.maneline.$1
class_java_src=$(ls */src/main/java/$(echo $class | sed -e 's^\.^/^g').java)
ls -l $class_java_src

jar_file=./desktop-app/build/libs/maneline-cli-0.0.0.jar
javac -proc:full -cp $jar_file -d ./_work $class_java_src
find _work -name $(basename $class_java_src|sed -e 's^java^class^') -ls

shift

jar_dir=$(dirname $jar_file)
# The next line was required when attempting to integrate tinyb
# Not required now that it is no longer built.
# unzip -o -d $jar_dir $jar_file native/linux/x86_64/*.so
jni_path_arg="-Djava.library.path=$jar_dir/native/linux/x86_64"
java -ea -cp ./_work:$jar_file $jni_path_arg $class "$@"