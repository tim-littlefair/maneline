#!/bin/sh

if [ ! "$(whoami)" = "root" ]
then
    echo This script must be run with root credentials
    exit 101
elif [ ! -d "$BLUEZ588_BASE" ]
then 
    echo No path to bluez installation found
    exit 102
fi

disable_systemd_bluez() {
    systemctl disable bluetooth
}

enable_systemd_bluez() {
    systemctl enable bluetooth
}

rundir=_work/_mbs_$$
mkdir $rundir

trap SIGINT enable_systemd_bluez

disable_systemd_bluez

$BLUEZ588_BASE/src/bluetoothd -E --debug --nodetach > $rundir/bluetoothd.log &
$BLUEZ588_BASE/monitor/btmon --no-pager> $rundir/btmon.log &

sleep 3600

killall bluetoothd
killall btmon

enable_systemd_bluez

