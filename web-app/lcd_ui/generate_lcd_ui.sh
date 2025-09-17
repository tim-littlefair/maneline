#! /bin/bash

template=$(dirname $0)/lcd-480x320-template.svg
ipaddress=$(ip addr | grep wlan0 | grep inet | grep -Eo "[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+" | head -1)
swversion=0.2.0+beta99.fffffff.999
ampname="Not detected yet"
psslot=41
psname1=EVENING
psname2=RAGE
module1=SimpleCompressor
module2=TriangleFlanger
module3=Twin65
module4=MonoDelay
module5=LargeHallReverb

svg_text=$(
  cat $template |
  sed -e s/ipaddress/$ipaddress/ |
  sed -e s/swversion/$swversion/
)

echo $svg_text
echo $svg_text | rsvg-convert - -o lcd.png



