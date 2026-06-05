#! /bin/bash
# prepare_maneline_sdcard.sh

usage() {
    cat <<+

Usage: $0 [balena device type] [WiFi_SSID] [WiFi_Password] { --hotspot }

Script to preconfigure an SD card for usage with the Maneline app.
The script will install a device driver overlay for the Waveshare 3.5"
LCD screen for RPi, and will configure the Pi to either to connect to
a local WiFi access point, or to offer a hotspot WiFi access point
of its own.

The following balena device types have been tested:
    raspberrypi0-2w-64, raspberrypi3-64, raspberrypi4-64, raspberrypi5
Other 64bit RPi models may or may not work.

WiFi SID and password are required to make the device connectable.

If the optional '--hotspot' argument is specified, the Balena device
will act as a WiFi hotspot accepting connections with the SSID and
password specified, otherwise it will attempt to connect to a local
WiFi access point serving the specified SSID.

If '--hotspot' is specified, the script will create a PNG matching the
image basename which can be scanned to connect to the hotspot.

This script only runs on Linux, and requires 'sudo' access to mount
the disk image file retrieved from balena.io so that the filesystem
changes required can be applied.

+
}

. balena-sdcard/balena_sdcard_functions.sh

balena_device_type=$1

if [ -z "$ml_ssid_1$ml_ssid_hs" ]
then
	wifi_ssid=$2
	wifi_password=$3
	opt_hotspot_switch=$4
fi

if [ -z "$balena_device_type" ] 
then
    usage
    exit 91
fi

CACHE_DIR=_work/cache
OUTPUT_DIR=_work/output
MOUNT_DIR=_work/mount
for d in $CACHE_DIR $OUTPUT_DIR $MOUNT_DIR
do
    if [ -d "$d" ]
    then
        echo $(basename $d) directory found
    else
        mkdir -p $d
        if [ "$?" = "0" ]
        then
            echo $(basename $d) directory created at $d
        else
            echo "\nfailed to create $(basename $d) directory at $d\n"
            exit 92
        fi
    fi
done

WORKING_IMAGE_PATH=/tmp/maneline-$wifi_ssid-$balena_device_type.img
OUTPUT_IMAGE_PATH=$OUTPUT_DIR/maneline-$wifi_ssid-$balena_device_type.img
for f in $WORKING_IMAGE_PATH $OUTPUT_IMAGE_PATH
do
    if [ -f $f ]
    then
        rm -f $f
    fi
done

verify_linux_sudo_access
verify_balena_cli
get_os
configure_os
configure_wifi_1
configure_wifi_hs
install_overlay_and_connections
flash_to_sdcard

exit 0







