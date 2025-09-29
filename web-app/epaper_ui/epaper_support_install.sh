#!/bin/sh

# The following steps are required to enable use of the Waveshare 2.13 e-Paper HAT
# to advertise the wifi parameters and/or Maneline app URL
# See:
# https://web.archive.org/web/20250903031237/https://www.waveshare.com/wiki/2.13inch_e-Paper_HAT_Manual#Working_With_Raspberry_Pi
# for the instructions this section covers

cd $(dirname $0)
apt-get install -y --no-install-suggests --no-install-recommends build-essential python3 python3-setuptools \
    python3-pip python3-pil python3-numpy python3-spidev python3-gpiozero python3-liblgpio 

if [ "0" = "1" ]
then
    LIBLGPIO=/usr/local/lib/liblgpio.so.1
    if [ -f $LIBLGPIO ]
    then
        echo $LIBLGPIO already built and installed
    else
        wget https://github.com/joan2937/lg/archive/master.zip
        unzip master.zip
        cd lg_master && make install && cd ..
    fi

    apt-get install -y --no-install-recommends --no-install-suggests gpiod libgipid-dev

    LIBBCM2835=/usr/local/lib/libbcm2835.a
    if [ -f $LIBBCM2835 ]
    then 
        echo $LIBBCM2835 already built and installed
    else
        wget http://www.airspayce.com/mikem/bcm2835/bcm2835-1.71.tar.gz
        tar xzvf bcm2835-1.71.tar.gz
        cd bcm2835-1.71 && ./configure && make && make check && make install && cd ..
    fi

    # Instructions related to WiringPi are confusing, and vary between hardware releases
    # As this item is marked as optional we leave it out for now
fi

wget https://files.waveshare.com/upload/7/71/E-Paper_code.zip
unzip E-Paper_code.zip -d e-Paper
#cd e-Paper/RaspberryPi_JetsonNano/c
#make clean
#make -j4 EPD=epd2in13V3
#./epd
mv e-Paper/RaspberryPi_JetsonNano/python/lib .
mv e-Paper/RaspberryPi_JetsonNano/python/pic .

exit 0
