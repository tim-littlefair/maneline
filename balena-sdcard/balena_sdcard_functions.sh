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
    if [ -r $CACHE_DIR/$balena_device_type.img ]
    then
        echo cached copy of OS found - will be used
    else
        balena os download $balena_device_type -o $CACHE_DIR/$balena_device_type.img
        if [ "$?" != "0" ]
        then
            echo "\nfailed to download OS\n"
            usage
            exit 3
        fi
    fi
    cp -f $CACHE_DIR/$balena_device_type.img $WORKING_IMAGE_PATH
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
    balena os configure $WORKING_IMAGE_PATH \
        --fleet gh_tim_littlefair/maneline-staging \
        --device-type $balena_device_type \
        --config-network ethernet \
        --dev
    config_outcome=$?
    if [ "$config_outcome" = "0" ]
    then
        echo "OS image configured"
    else
        echo "\nfailed to configure OS\n"
        exit 4
    fi
    balena preload $WORKING_IMAGE_PATH --fleet gh_tim_littlefair/maneline-staging \
        --commit current
    if [ "$?" = "0" ]
    then
        echo "preloaded application"
    else
        echo "\nfailed to preload application\n"
        exit 5
    fi
}

configure_wifi_1() {
    if [ ! -z "$ml_ssid_1" ]
    then
        wifi_cxn_1=/tmp/balena-wifi-01
        cat balena-sdcard/balena-wifi-01 | \
            sed -e "s/%ml_ssid_1%/$ml_ssid_1/" | \
            sed -e "s/%ml_pswd_1%/$ml_pswd_1/" \
        > $wifi_cxn_1
        cat $wifi_cxn_1
    else
        wifi_cxn_1=""
    fi
}

configure_wifi_hs() {
    if [ ! -z "$ml_ssid_hs" ]
    then
        wifi_cxn_hs=/tmp/balena-hotspot
        cat balena-sdcard/balena-hotspot | \
            sed -e "s/%ml_ssid_hs%/$ml_ssid_hs/" | \
            sed -e "s/%ml_pswd_hs%/$ml_pswd_hs/" \
        > $wifi_cxn_hs
        cat $wifi_cxn_hs
    else
        wifi_cxn_hs=""
    fi
}

install_overlay_and_connections() {
    partition1_params=$(fdisk -l $WORKING_IMAGE_PATH | sed -e 's/*//' | grep img1)
    start_sector=$(echo $partition1_params | ( read a b c d e f g h; echo $b ))
    sector_count=$(echo $partition1_params | ( read a b c d e f g h; echo $d ))
    sudo mount -t vfat \
        -o loop,rw,offset=$(($start_sector*512)),sizelimit=$(($sector_count*512)) \
        $WORKING_IMAGE_PATH $MOUNT_DIR
    sudo cp balena-sdcard/tft35a.dtbo $MOUNT_DIR/overlays
    sudo cp balena-sdcard/config.txt $MOUNT_DIR

    for c in "$wifi_cxn_1" "$wifi_cxn_hs" 
    do
        if [ ! -z "$c" ]
        then
            sudo cp $c $MOUNT_DIR/system-connections
            rm $c
        fi
    done

    sleep 1
    sudo sync ; sudo sync
    sleep 1
    sudo umount $MOUNT_DIR
    mv -f $WORKING_IMAGE_PATH $OUTPUT_IMAGE_PATH
}

flash_to_sdcard() {
    drive=/dev/sdb
    balena util available-drives | grep $drive > /dev/null
    flashing_cmd="balena os initialize $OUTPUT_IMAGE_PATH --type $balena_device_type --drive $drive"
    if [ "$?" = "0" ]
    then
        echo "flashing to $drive"
        $flashing_cmd
        echo Additional SD cards can be initialized using the following command:
        echo $flashing_cmd
    else
        echo "\n$drive not present - manually flash using the following command:"
        echo $flashing_cmd
    fi
}

balena_device_type=$1
if [ -z "$balena_device_type" ]
then
    usage
    exit 91
fi


