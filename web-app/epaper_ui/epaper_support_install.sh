#!/bin/sh

# The following steps are required to enable use of the Waveshare 2.13 e-Paper HAT
# to advertise the wifi parameters and/or Maneline app URL
# See:
# https://web.archive.org/web/20250903031237/https://www.waveshare.com/wiki/2.13inch_e-Paper_HAT_Manual#Python
# for the list of packages which need to be installed
# Note that Debian/APT package python3-spidev is installed 
# instead of following the instruction to install PyPi 
# package spidev, so python3-pip is not required.

apt-get update
apt-get install -y --no-install-suggests --no-install-recommends \
    python3 python3-pil python3-numpy python3-spidev python3-gpiozero python3-lgpio 

wget https://files.waveshare.com/upload/7/71/E-Paper_code.zip
unzip E-Paper_code.zip -j -d epaper_ui/lib RaspberryPi_JetsonNano/python/lib
unzip E-Paper_code.zip -j -d epaper_ui/pic RaspberryPi_JetsonNano/python/pic
rm E-Paper_code.zip
ls -lR epaper_ui

exit 0
