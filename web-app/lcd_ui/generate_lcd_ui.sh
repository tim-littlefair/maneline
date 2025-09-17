#! /bin/bash

template=$(dirname $0)/lcd-480x320-template.svg
ipaddress=$(ip addr | grep -e wlan0 -e wlp3s0 | grep inet | grep -Eo "[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+" | head -1)
swversion=0.2.0+beta99.fffffff.999
ampname="Not detected yet"
psslot=41
psname1=EVENING
psname2=RAGE
psmodule1=SimpleCompressor
psmodule2=TriangleFlanger
psmodule3=Twin65
psmodule4=MonoDelay
psmodule5=LargeHallReverb

svg_text=$(
  cat $template |
  sed -e "s/##/$psslot/" |
  sed -e "s/@ipaddress/$ipaddress/" |
  sed -e "s/@swversion/$swversion/" |
  sed -e "s/@ampname/$ampname/" |
  sed -e "s/@psname1/$psname1/" |
  sed -e "s/@psname2/$psname2/" |
  sed -e "s/@psmodule1/$psmodule1/" |
  sed -e "s/@psmodule2/$psmodule2/" |
  sed -e "s/@psmodule3/$psmodule3/" |
  sed -e "s/@psmodule4/$psmodule4/" |
  sed -e "s/@psmodule5/$psmodule5/"
)

echo $svg_text | rsvg-convert - -o lcd.png



