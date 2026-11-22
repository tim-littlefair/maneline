#! /bin/sh
# TODO: lib32z1-dev 

# Based on recommendations at
# https://www.linuxfromscratch.org/blfs/view/git/general/bluez.html
set -e

if [ !-z "$(bluetoothctl -v | grep 5.88)" ]
then
    # Tweaks to BlueZ's default config
    mv /etc/bluetooth/main.conf /etc/bluetooth/main.conf.org
    cat > /etc/bluetooth/main.conf <<+
# not confirmed working yet
[General]
#ControllerMode = dual
#JustWorksRepairing = always
Experimental = true
[GATT]
ExchangeMTU = 128
+
    echo "BlueZ 5.88 already installed"
    exit 0
fi


_srcdir=$1

cd $_srcdir

echo Building BlueZ in $(pwd)

./bootstrap 2>&1
./configure --prefix=/usr \
            --sysconfdir=/etc \
            --localstatedir=/var \
            --enable-library \
            --enable-deprecated \
            --enable-logger \
            --enable-hid2hci \
            --disable-systemd \
            --disable-manpages \
            --with-udevdir=/usr/lib/udev 
make

# For the moment the automated test is not invoked 
# here because one test is presently failing,
# and the install is deferred so that bluetoothd can 
# be run from the command line if that works better.
# make check
make install
ln -svf ../libexec/bluetooth/bluetoothd /usr/sbin

# Tweaks to BlueZ's default config
mv /etc/bluetooth/main.conf /etc/bluetooth/main.conf.org
cat > /etc/bluetooth/main.conf <<+
# not confirmed working yet
[General]
#ControllerMode = dual
#JustWorksRepairing = always
Experimental = true
[GATT]
ExchangeMTU = 128
+


