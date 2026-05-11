#! /bin/bash
# prepare_maneline_sdkcard.sh

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

verify_linux_sudo_access() {
    uname | grep -i Linux > /dev/null
    if [ "$?" != "0" ]
    then
        echo "\nThis script only runs on Linux\n"
        usage
        exit 1
    fi

    sudo echo "sudo access verified"
    if [ "$?" != "0" ]
    then
        echo "\nsudo access verification failed\n"
        usage
        exit 2
    fi
}

verify_balena_cli() {
    which balena > /dev/null
    if [ "$?" != "0" ]
    then
        echo "\nbalena CLI executable not found\n"
        usage
        exit 3
    fi

}
get_os() {
    if [ -r $CACHE_DIR/$1.img ]
    then
        echo cached copy of OS found - will be used
    else
        balena os download $1 -o $CACHE_DIR/$1.img
        if [ "$?" != "0" ]
        then
            echo "\nfailed to download OS\n"
            usage
            exit 3
        fi
    fi
    cp -f $CACHE_DIR/$1.img $WORKING_IMAGE_PATH
    if [ "$?" = "0" ]
    then
        echo "working copy of image created"
    else
        echo "\nfailed to copy OS\n"
        usage
        exit 3
    fi
}

configure_os() {
    # args: ssid password { hotspot }
    if [ -z "$opt_hotspot_switch" ]
    then
        cxn_fname=/tmp/local-wifi
        cat > $cxn_fname <<+
[connection]
id=local-wifi
type=wifi

[wifi]
hidden=true
mode=infrastructure
ssid=$1

[ipv4]
method=auto

[ipv6]
addr-gen-mode=stable-privacy
method=auto

[wifi-security]
auth-alg=open
key-mgmt=wpa-psk
psk=$2
+
    elif [ "--hotspot" = "$opt_hotspot_switch" ]
    then
        cxn_fname=/tmp/balena-hotspot
        cat > $cxn_fname <<+
[connection]
id=balena-hotspot
uuid=36060c57-aebd-4ccf-aba4-ef75121b5f77
type=wifi
autoconnect=true
interface-name=wlan0
permissions=
secondaries=

[wifi]
band=bg
mac-address-blacklist=
mac-address-randomization=0
mode=ap
seen-bssids=
ssid=$1

[wifi-security]
group=
key-mgmt=wpa-psk
pairwise=
proto=rsn
psk=$2

[ipv4]
dns-search=
method=shared

[ipv6]
addr-gen-mode=stable-privacy
dns-search=
method=auto

[ipv4]
method=auto

[ipv6]
addr-gen-mode=stable-privacy
method=auto

[wifi-security]
auth-alg=open
key-mgmt=wpa-psk
psk=$2
+
    else
        echo "\nUnexpected value '$opt_hotspot_switch' for optional hotspot switch\n"
        exit 4
    fi
    balena os configure $WORKING_IMAGE_PATH \
        --fleet gh_tim_littlefair/maneline-fleet \
        --device-type $balena_device_type \
        --config-network ethernet \
        --system-connection $cxn_fname \
        --dev
    config_outcome=$?
    rm $cxn_fname
    if [ "$config_outcome" = "0" ]
    then
        echo "OS image configured"
    else
        echo "\nfailed to configure OS\n"
        exit 4
    fi
    balena preload $WORKING_IMAGE_PATH --fleet gh_tim_littlefair/maneline-fleet
    if [ "$?" = "0" ]
    then
        echo "preloaded application"
    else
        echo "\nfailed to preload application\n"
        exit 5
    fi

}

install_overlay() {
    if [ -f $CACHE_DIR/waveshare35a-overlay.dtbo ]
    then
        echo cached copy of DTBO found - will be used
    else
        tar xzvf ./lcd-driver/LCD-show-180331.tar.gz \
            LCD-show/waveshare35a-overlay.dtb --to-stdout > $CACHE_DIR/waveshare35a-overlay.dtbo
        echo DTBO file unpacked
    fi

    partition1_params=$(fdisk -l $WORKING_IMAGE_PATH | sed -e 's/*//' | grep img1)
    start_sector=$(echo $partition1_params | ( read a b c d e f g h; echo $b ))
    sector_count=$(echo $partition1_params | ( read a b c d e f g h; echo $d ))
    sudo mount -t vfat \
        -o loop,rw,offset=$(($start_sector*512)),sizelimit=$(($sector_count*512)) \
        $WORKING_IMAGE_PATH $MOUNT_DIR
    sudo cp $CACHE_DIR/waveshare35a-overlay.dtbo $MOUNT_DIR/overlays
    sudo umount $MOUNT_DIR
    mv -f $WORKING_IMAGE_PATH $OUTPUT_DIR
}

flash_to_sdcard() {
    drive=/dev/mmcblk0
    balena util available-drives | grep $drive > /dev/null
    if [ "$?" = "0" ]
    then
        echo "flashing to $drive"
        balena os initialize $WORKING_IMAGE_PATH --type $balena_device_type --drive $drive
    else
        echo "\n$drive not present - manually flash using $WORKING_IMAGE_PATH\n"
        exit 0
    fi
}

balena_device_type=$1
wifi_ssid=$2
wifi_password=$3
opt_hotspot_switch=$4


if [ -z "$balena_device_type" ] || [ -z "$wifi_ssid" ] || [ -z "$wifi_password" ]
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

WORKING_IMAGE_PATH=/tmp/maneline-$balena_device_type.img
if [ -f $WORKING_IMAGE_PATH ]
then
    rm -f $WORKING_IMAGE_PATH
fi

verify_linux_sudo_access
verify_balena_cli
get_os $balena_device_type
configure_os
install_overlay
flash_to_sdcard







