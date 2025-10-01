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

cat > /usr/lib/systemd/system/maneline-startup.service <<+
[Unit]
Description=Display Maneline startup message on e-paper
ConditionACPower=true
Before=network.target

[Service]
Type=oneshot
ExecStart=/usr/bin/python3 /home/maneline/epaper_ui/epaper_ui.py --starting-up
+

cat > /usr/lib/systemd/system/maneline-shutdown.service <<+
[Unit]
Description=Display Maneline shutdown message on e-paper
ConditionACPower=true
Before=final.target

[Service]
Type=oneshot
ExecStart=/usr/bin/python3 /home/maneline/epaper_ui/epaper_ui.py --shutting-down
+

/usr/bin/systemctl enable maneline-startup.service
/usr/bin/systemctl enable maneline-shutdown.service

exit 0
