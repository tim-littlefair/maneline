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

find_fhau_file() {
  fhau_file=$(find . -name "$1" -print)
  if [ -z "$fhau_file" ]
  then
    echo "
  No file matching $1 found.
  Suggested values: TBD
  "
    exit 1
  fi
  echo $fhau_file
}

gen_target=./_work/FMICStartupExample.java
h4j_example=$h4j_dir/src/test/java/org/hid4java/examples/FMICStartupExample.java

grep -B100 -A1 "private void startProvider()" $h4j_example > $gen_target
duap_path=$(find_fhau_file DesktopUsbAmpProvider.java)

cat $duap_path \
  | grep -B0 -A1000 "private void startProvider()" \
  | grep -v "private void startProvider()" \
  | sed -e "s/    /  /g" \
  >> $gen_target

classes_to_copy="ProtocolDeviceInterface FMICProtocolBase LTSeriesProtocol"
classes_to_copy="$classes_to_copy PresetRegistryBase PresetRecordBase PresetRegistryVisitor PresetNameListGenerator"
for f in $classes_to_copy
do
  p=$(find_fhau_file $f.java)
  echo Processing $p
  cat $p \
    | grep -F -B1 -A1000 -e "/**" -e "class $f" -e "interface $f" \
    | sed -e "s/    /  /g" \
    | sed -e "s/public class/class/" \
    >> $gen_target
done

meld $h4j_example $gen_target

exit 1







