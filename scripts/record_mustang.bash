#!/bin/bash
# record_mustang.bash

# Script to record output from Mustang LT40S
# NB This script depends on bash-specific behaviour in the 'read' builtin
# It will fail if executed under /bin/sh (or variants like /bin/dash, /bin/ash).

usage() {
  echo 'Usage: $0 { record_method_switch } recording_name'
  echo 'If record_method_switch is present it must be one of:'
  echo '    --maneline'
  echo '    --ffmpeg'
  echo ' or --arecord'
  echo 'If record_method_switch is not present it defaults to --maneline behaviour'
  exit 1
}

ffmpeg_start() {
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
  arecord --quiet --device="$device_name" --channels=2 --format=S16_LE --rate=48000 > $1 &
  record_pid=$!
}

maneline_audiorecorder_start() {
  # This uses the test CLI implemented in static void AudioRecorder.main(String argv[])
  $(dirname $0)/run_class_main.sh desktop_app.AudioRecorder $1 &
  rcm_pid=$!

  # Give the Java process time to start up and start the recording child process
  sleep 2

  # To stop the background subprocess, we need to send a signal to the
  # ffmpeg child process started by AudioRecorder.startRecording(...)
  # so we have to navigate to the subprocess's PID
  java_pid=$(ps --ppid $rcm_pid --no-headers --format pid)
  ffmpeg_pid=$(ps --ppid $java_pid --no-headers --format pid)
  #ps -elf | grep -e run_class_main -e AudioReorder -e ffmpeg
  #echo java_pid=$java_pid ffmpeg_pid=$ffmpeg_pid
  record_pid=$ffmpeg_pid
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
  arecord --quiet --device="$device_name" --channels=2 --format=S16_LE --rate=48000 > $1 &
  record_pid=$!
}


if [ "$1" = "--maneline" ]
then
  record_method="maneline"
  shift
elif [ "$1" = "--arecord" ]
then
  record_method=arecord
  shift
else
  record_method=maneline
fi

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
echo Recording will stop unconditionally after $MAX_DURATION seconds if not ended earlier

case $record_method in
  maneline) maneline_audiorecorder_start $wav_filename;;
  arecord) linux_arecord_start $wav_filename;;
  *) echo "Unexpected record method $record_method" && exit 3
esac

read -t $MAX_DURATION -s -p "Press ENTER to end recording"
echo

kill -TERM $record_pid

echo Converting to FLAC format
end_time=$(date +%s)
duration=$(($end_time-$start_time))
flac_filename=$recording_filename_prefix-$start_time-${duration}s.flac

# Error redirection and grep filter out warnings caused because
# sample count written to header at start of recording does not
# reflect the actual length of recording.
flac $wav_filename --silent -o $flac_filename 2>&1 | grep -v -e "WARNING"

echo Files generated for this recording:
ls -l ${recording_filename_prefix}-$start_time*
