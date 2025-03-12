#! /bin/sh
# compare_with_hid4java.sh

# This script is intended to simplify the task of synchronizing
# changes from classes in FHAU to the FMICStartupExample.java
# example in Tim Littlefair's fork of the Gary Rowe's hid4java package.

h4j_dir=../hid4java
if [ ! -x /usr/bin/meld ]
then
  echo "
This script requires the 'meld' file comparison tool to be installed.
On Ubuntu/Debian systems this can be installed with the command
    sudo apt install meld
"
  exit 1
elif [ ! -d  $h4j_dir ]
then
  echo "
This script requires a checkout of Tim Littlefair's fork of the
hid4java git repository at $h4j_dir relative to the FHAU repository
containing the script.
This can be checked out with the following commands:
    git -C .. clone git@github.com:tim-littlefair/hid4java.git
"
  exit 1
fi

fhau_file=$(find . -name "$1" -print)
if [ -z "$fhau_file" ]
then
  echo "
No file matching $2 found.
Suggested values: TBD
"
  exit 1
fi

meld $h4j_dir/src/test/java/org/hid4java/examples/FMICStartupExample.java $fhau_file &




