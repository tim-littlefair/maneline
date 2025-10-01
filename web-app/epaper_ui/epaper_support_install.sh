#!/bin/sh

# The following steps are required to enable optional use of the Waveshare 2.13 e-Paper HAT
# to advertise the wifi parameters and/or Maneline app URL.
# As at September 2025, the optional hardware is for sale in Australia at:
# 
# See:
# https://web.archive.org/web/20250903031237/https://www.waveshare.com/wiki/2.13inch_e-Paper_HAT_Manual#Python
# for the list of packages which need to be installed.
# Note that Debian/APT package python3-spidev is installed 
# instead of following the instruction to install PyPi 
# package spidev, so python3-pip is not required.
# This service depends on the 

apt-get update
apt-get install -y --no-install-suggests --no-install-recommends --fix-missing \
    python3 python3-pil python3-numpy python3-spidev python3-gpiozero python3-lgpio \
    vim iproute2

wget https://files.waveshare.com/upload/7/71/E-Paper_code.zip
unzip E-Paper_code.zip -d /tmp
mv /tmp/RaspberryPi_JetsonNano/python/lib epaper_ui/lib
mv /tmp/RaspberryPi_JetsonNano/python/pic epaper_ui/pic
rm -rf /tmp/RaspberryPi_JetsonNano/ E-Paper_code.zip 

exit 0
