#!/bin/sh
# Author: Tim Littlefair
# Copying: see ../LICENSE (GPL v2)

# The initial logic in this file was entirely provided by the 
# document ../MUSTANG_MICRO_PLUS_LINUX.md
# which arrived in the maneline git repository as 
# part of a group of pull requests including
# https://github.com/tim-littlefair/maneline/pull/139
# raised by GitHub user @joba-1.

# I am very grateful to @joba-1 and @jonasjancarik for
# their contributions to the project, which are bringing
# integration of first class support for the Mustang Micro 
# Plus (and possibly other FMIC devices interoperable with 
# Fender Tone Mobile for iOS/Android) much closer to 
# feasibility.

# The purpose of this script is to automate manual steps
# recommended in the .md document referenced above.

# As of the 13 November 2026 version, the intent of this script
# is that it should be run manually under the root user's 
# credentials to pre-condition the BlueZ stack config/cache
# state for a specific MMP's Bluetooth address ready for 
# scripts/mmp_ble.py (or any other alternate connection 
# script or application) to run on it.  

# Note that as at this date, I (Tim L) have not yet achieved a 
# single successful 'mmp_ble.py status' run on the hardware 
# available to me, although I have had one successful connection
# (and a few failed ones) using @joba-1's preset editor project 
# from https://github.com/joba-1/ChordsPlay.

# At least some steps in this script are likely to require root 
# privileges, so for the time being, it will be 
# Ultimately I hope that this script will become something 
# which can be run automatically once when a new MMP is first 
# detected or is  found to need re-pairing, or to require 
# action to connect cleanly for other reasons.  Before we 
# can get to that state I expect to make frequent tweaks to 
# this file 


usage() {
    echo ""
    echo "Usage: $0 [mmp_bd_addr] { [host_bd_addr] }"
    echo "    mmp_bd_addr is the colon-separated Bluetooth address of the MMP"
    echo "    host_bd_addr is optional, unless the host has multiple bluetooth"
    echo "    adaptors, in which case it must be the colon-separated Bluetooth"
    echo "    address of the adaptor to be used for setup"
    echo ""
}

debug_btctl_info() {
    if [ true ]
    then
        echo ">>> $1 >>>"
        bluetoothctl info $mmp_bd_addr
        echo "<<< $1 <<<"
    fi
}

if [ ! "$(whoami)" = "root" ]
then
    echo This script expects to be run with root credentials
    exit 101
elif [ -z "$1" -o "$1" = "--help" ]
then
    usage
    exit 1
else
    mmp_bd_addr=$1
    host_bd_addr=$2
fi

bt_main_config=/etc/bluetooth/main.conf
bluez_experimental_enabled=$(grep -E '^Experimental = true$' $bt_main_config )
if [ -z "$bluez_experimental_enabled" ]
then
    echo "In the file $bt_main_config, please ensure that there"
    echo "is a line which matches: 'Experimental = true'."
    echo "The match must be exact - no leading space or comment"
    echo "marker, no trailing space, exactly one space either side"
    echo "of '='."
    echo "If the file needs to edited run:"
    echo "sudo systemctl restart bluetooth"
    echo "to ensure it is re-read."
    exit 102
else
    echo $bt_main_config enables experimental features as required
fi 

if [ -z $host_bd_addr ]
then
    for c in $(ls /var/lib/bluetooth)
    do
        if [ ! -z $host_bd_addr ]
        then
            echo Host has more than one Bluetooth adaptor, specify
            echo which one is to be used for the setup
            exit 103
        else
            host_bd_addr=$c
        fi
    done
fi

mmp_dbus_addr=/org/bluez/hci0/dev_$(echo $mmp_bd_addr | sed -e s/:/_/g -)

# Do a pairing over Bluetooth LE, but leave the device
# set to prefer BR/EDR so that the audio headset behaviour 
# continues to work
debug_btctl_info "before le pairing"
busctl --system set-property org.bluez $mmp_dbus_addr org.bluez.Device1 PreferredBearer s le
bluetoothctl pair $mmp_bd_addr
bluetoothctl trust $mmp_bd_addr             
busctl --system set-property org.bluez $mmp_dbus_addr org.bluez.Device1 PreferredBearer s bredr
debug_btctl_info "after le pairing"

# Remove cached value of characteristic '2b29 value if present
# Presence of this cache item causes BlueZ to request its value 
# on future connections, which causes the connection to hang
adaptor_mmp_cache=/var/lib/bluetooth/$host_bd_addr/cache/$mmp_bd_addr
if [ -f $adaptor_mmp_cache ]
then
    systemctl stop bluetooth
    sed -i '/^0005=2803:0006:0a:00002b29-/d' $adaptor_mmp_cache
    cat $adaptor_mmp_cache
    systemctl start bluetooth
    # Allow some time for the adaptor to come up
    sleep 2
    # Do a BR/EDR connection to leave the device 
    bluetoothctl connect $mmp_bd_addr
else
    echo cache for $mmp_bd_addr via $host_bd_addr did not exist
fi

