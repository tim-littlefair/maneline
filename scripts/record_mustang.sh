#!/bin/bash
# record_mustang.sh
# Script to record output from Mustang LT40S
# TODO: device name presently hard-coded, should
# be detected.

handle_ctrl_c()
{
  echo Stopping arecord
  killall -TERM arecord
}
trap handle_ctrl_c INT

start_time=$(date +%s)
wav_filename=$start_time.wav
arecord --device="hw:3,0" -f dat --channels=2 --format=s16_le --rate=48000 > $wav_filename
end_time=$(date +%s)
duration=$(($end_time-$start_time))
flac_filename=$start_time_$duration.flac
flac $wav_filename -o $flac_filename

ls -l $start_time*
