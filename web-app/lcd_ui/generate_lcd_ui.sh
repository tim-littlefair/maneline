#! /bin/bash

template=$(dirname $0)/lcd-480x320-template.svg
ipaddress=$(ip addr | grep -e wlan0 -e wlp3s0 | grep inet | grep -Eo "[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+" | head -1)
swversion=v0.2.0+beta99
ampname="Not connected"
psslot=41
psname1=EVENING
psname2=RAGE
psmodule1=s:SimpleCompressor
psmodule2=m:TriangleFlanger
psmodule3=a:Twin65
psmodule4=d:MonoDelay
psmodule5=r:LargeHallReverb

icon_data_base64=$(base64 --wrap=0 < assets/maneline-icon.png)
icon_data_uri="data:image/png;base64,$icon_data_base64"
qrcode_data_base64=$(zint -b QRCODE -d http://$ipaddress:8080 --filetype=PNG --direct | base64 --wrap=0)
qrcode_data_uri="data:image/png;base64,$qrcode_data_base64"


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
  sed -e "s/@psmodule5/$psmodule5/" |
  sed -e "s^@icon_data_uri^$icon_data_uri^" |
  sed -e "s^@qrcode_data_uri^$qrcode_data_uri^"
)

echo $svg_text | rsvg-convert - -o lcd.png
if [ ! "$?" = 0 ]
then
  echo SVG Text:
  echo $svg_text
fi




