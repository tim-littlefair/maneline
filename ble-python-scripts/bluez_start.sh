#!/bin/sh

bluez_dir=./bluez-5.88

killall bluetoothd
$bluez_dir/src/bluetoothd -f ./_etc_bluetooth_main.conf


