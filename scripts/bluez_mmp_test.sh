#!/bin/sh

if [ -z "$BLUEZ_HOME" ] || [ ! -x "$BLUEZ_HOME/src/bluetoothd" ]
then
    echo This script requires environment variable BLUEZ_HOME
    echo which must point at a standalone build of bluez-5.88 or later
    exit 21
fi


sudo systemctl stop bluetooth
sudo killall bluetoothd

sh -c sudo xterm -e "$BLUEZ_HOME/bin/bluetoothd -E --no-detach" &
sleep 1





