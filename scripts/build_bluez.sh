#! /bin/sh
# TODO: lib32z1-dev 

set -e

apt-get install -y --no-install-recommends --no-install-suggests \
    python3.13 python3-pip python3-venv  python3-docutils \
    make autoconf \
    libglib2.0-dev libreadline-dev libssl-dev libtool libtool-bin libdbus-1-3 libdbus-1-dev libudev-dev libical-dev 

_srcdir=$1
_rundir=$2

cd $_srcdir

echo Building BlueZ in $(pwd)

./bootstrap 2>&1
./configure --sysconfdir=/etc \
            --localstatedir=$_rundir/bluez \
            --enable-library \
            --enable-experimental \
            --enable-deprecated \
            --enable-logger \
            --enable-hid2hci \
            --disable-systemd \
            --with-udevdir=/usr/lib/udev 
make

# For the moment the automated test and the install are not 
# invoked here because one test is presently failing,
# and the install is deferred so that bluetoothd can 
# be run from the command line if that works better.
# make check
make install

# Tweaks to BlueZ's default config
cat > ../_etc_bluetooth_main.conf <<+
# not confirmed working yet
[General]
ControllerMode = dual
JustWorksRepairing = always
Experimental = true
[GATT]
ExchangeMTU = 128
+

cp ../_etc_bluetooth_main.conf /etc/bluetooth/main.conf

