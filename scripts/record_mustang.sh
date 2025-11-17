#!/bin/bash
# record_mustang.sh
# Script to record output from Mustang LT40S
# TODO: device name presently hard-coded, should
# be detected.

set_device_name_linux()
{
  proc_card_line=$(grep Mustang /proc/asound/cards | head -1)
  if [ -z "$proc_card_line" ]
  then
    echo No Mustang device found
    exit 1
  fi
  card_number_string=$(echo $proc_card_line|cut -c1-2)
  card_number=$(($card_number_string))
  device_name=hw:$card_number,0
  echo Detected card details: $proc_card_line
  echo Recording from device $device_name
}

handle_ctrl_c()
{
  echo Stopping arecord
  killall -TERM arecord
}
trap handle_ctrl_c INT

if [ "$(uname)" = "Linux" ]
then
  set_device_name_linux
else
  echo Recording presently only supported on Linux
  exit 2
fi

start_time=$(date +%s)
wav_filename=$start_time.wav
arecord --device="$device_name" --channels=2 --format=S16_LE --rate=48000 > $wav_filename
end_time=$(date +%s)

duration=$(($end_time-$start_time))
flac_filename=$start_time-$duration.flac
flac $wav_filename -o $flac_filename

ls -l $start_time*
