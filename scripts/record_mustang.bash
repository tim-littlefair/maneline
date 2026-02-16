#!/bin/bash
# record_mustang.bash

# Script to record output from Mustang LT40S
# NB This script depends on bash-specific behaviour in the 'read' builtin
# It will fail if executed under /bin/sh (or variants like /bin/dash, /bin/ash).

usage() {
  echo 'Usage: $0 [recording_name]'
  exit 1
}

linux_arecord_start() {
  if [ ! "$(uname)" = "Linux" ]
  then
    echo This mode of recording presently only supported on Linux
    exit 2
  fi

  proc_card_line=$(grep Mustang /proc/asound/cards | head -1)
  if [ -z "$proc_card_line" ]
  then
    echo No Mustang device found
    exit 3
  fi
  card_number_string=$(echo $proc_card_line|cut -c1-2)
  card_number=$(($card_number_string))
  device_name=hw:$card_number,0
  echo Detected card details: $proc_card_line
  echo Recording from device $device_name
  record_method=arecord
  arecord --quiet --device="$device_name" --channels=2 --format=S16_LE --rate=48000 > $1 &
}

recording_name=$1
if [ -z "$recording_name" ]
then
  usage
  # usage() exits with status 1
fi

record_dir=./_work/recordings
if [ ! -d $record_dir ]
then
  set -e
  mkdir $record_dir
  set +e
fi

recording_filename_prefix=$record_dir/$recording_name
start_time=$(date +%s)
wav_filename=$recording_filename_prefix-$start_time.wav

MAX_DURATION=3600
echo Recording will stop unconditionally after $MAX_RECORDING_SECONDS seconds if not ended earlier

linux_arecord_start $wav_filename
read -t $MAX_DURATION -s -p "Press any key to end recording"
echo

if [ "$record_method" = "arecord" ]
then
  killall arecord
else
  echo Unexpected record method!
fi

echo Converting to FLAC format
end_time=$(date +%s)
duration=$(($end_time-$start_time))
flac_filename=$recording_filename_prefix-$start_time-${duration}s.flac

# Error redirection and grep filter out 3 warnings caused because
# sample count written to header at start of recording does not
# reflect the actual length of recording.
flac $wav_filename --silent -o $flac_filename 2>&1 | grep -v -e "chunk" -e "unexpected EOF"

echo Files generated for this recording:
ls -l ${recording_filename_prefix}-$start_time*
