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

# As of the 22 November 2026 version, the intent of this script
# is that it should be run manually under the root user's 
# credentials to pre-condition the BlueZ stack config/cache
# state for a specific MMP's Bluetooth address ready for 
# ble-python-scripts/mmp_web.py (or any other alternate connection 
# script or application) to run on it.  

# A sequence which appears to work for me as of this version is:
# 0) Environment is Ubuntu 26.10 Stonking with BlueZ 5.87,
#    /etc/bluetooth/main.conf has been manually edited
#    to include the line 'Experimental = true' and 
#    Bluetooth has been restarted since the edit
# 1) Factory reset the MMP and turn it off
# 2) On the Ubuntu host system tray Blueooth manager
#    (blueman), forget the MMP if it is listed.
# 3) Switch the MMP on, put it in pairing mode, and 
#    pair using the Ubuntu blueman UI.
# 4) Disconnect and run the following command line:
#    'sh ble-python-scripts/bluez_setup.sh AA:BB:CC:DD:EE:FF'
# 5) Run python ble-python-scripts/mmp_web.py
# 6) Browse to http://localhost:8081
# Matrix of buttons for presets should appear within 
# a couple of seconds.
# Note that I have only tested this twice, as I am not 
# keen to do too many factory resets with the borrowed
# MMP I am using.

usage() {
    echo ""
    echo "Usage: $0 [mmp_bd_addr] { [host_bd_addr] }"
    echo "    mmp_bd_addr is the colon-separated Bluetooth address of the MMP"
    echo "    host_bd_addr is optional, unless the host has multiple bluetooth"
    echo "    adaptors, in which case it must be the colon-separated Bluetooth"
    echo "    address of the adaptor to be used for setup."
    echo "NB: $0 must be run with root credentials."
    echo ""
}

if [ -z "$1" -o "$1" = "--help" ]
then
    usage
    exit 1
elif [ ! "$(whoami)" = "root" ]
then
    echo This script expects to be run with root credentials
    exit 101
elif [ -z "$(grep -E '^Experimental = true$' /etc/bluetooth/main.conf )" ]
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
    mmp_bd_addr=$1
    host_bd_addr=$2
fi

debug_btctl_info() {
    if true
    then
        echo $1 begins
        bluetoothctl info $mmp_bd_addr
        echo $1 ends
    fi
}

set_bearer() {
    if [ ! -z "$(which busctl)" ]
    then
        busctl --system set-property org.bluez $mmp_dbus_addr org.bluez.Device1 PreferredBearer s $1
    else
        # WIP busctl is provided by systemd => not available on Balena/RPi container
        dbus_send_cmd_prefix='dbus-send --system --print-reply --type=method_call --dest=org.bluez $mmp_dbus_addr.SetProperty '
        echo $dbus_send_cmd_prefix"$1"
        $dbus_send_cmd_prefix"$1"
    fi
}


pair_and_bond_le() {
    set_bearer le
    if false || [ ! -z "$(which busctl)" ]
    then
        # WIP If busctl is available we might prefer to do an LE connect/pair/trust
        # via the org.bluez.Bearer.LE1 interface rather than switching PreferredBearer 
        # out to 'le' and back to 'bredr'
        busctl --system introspect org.bluez $mmp_dbus_addr org.bluez.Device1
        busctl --system introspect org.bluez $mmp_dbus_addr org.bluez.Bearer.LE1
        busctl --system call org.bluez $mmp_dbus_addr org.bluez.Device1 Connect
        busctl --system call org.bluez $mmp_dbus_addr org.bluez.Device1 Pair
        # busctl --system call org.bluez $mmp_dbus_addr org.bluez.Device1 Trust s $mmp_bd_addr
    else
        bluetoothctl connect $mmp_bd_addr
        bluetoothctl pair $mmp_bd_addr
        bluetoothctl trust $mmp_bd_addr             
    fi
    set_bearer last-used
}

if [ -z $host_bd_addr ]
then
    for c in $(ls /var/lib/bluetooth)
    do
        if [ ! -z "$host_bd_addr" ]
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
echo Operating on $mmp_bd_addr via $mmp_dbus_addr

# Do a pairing over Bluetooth LE, but leave the device
# set to prefer BR/EDR so that the audio headset behaviour 
# continues to work
debug_btctl_info "before le pairing"
pair_and_bond_le $mmp_dbus_addr
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
    # Do a BR/EDR connection to leave the device connected
    bluetoothctl connect $mmp_bd_addr
else
    echo cache for $mmp_bd_addr via $host_bd_addr did not exist
fi

