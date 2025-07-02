#!/bin/sh

restart_seconds=20

ls -lR .

lua ./run_pegasus.lua &

while true
do
    if [ ! -c /dev/hidraw0 ]
    then
        echo No HID USB device detected - is Mustang LT turned on?
        echo Container will restart in $restart_seconds seconds
        sleep $restart_seconds
        echo Restarting 
        exit
    else
        echo HID device OK
        sleep 60
    fi
done

