#! /bin/bash

generate_svg_text () {
  cat $template |
  sed -e "s/##/$psslot/" |
  sed -e "s/@ipaddress/$ipaddress/" |
  sed -e "s/@swversion/$swversion/" |
  sed -e "s/@ampname/$ampname/" |
  sed -e "s/@fwversion/$fwversion/" |
  sed -e "s/@psname1/$psname1/" |
  sed -e "s/@psname2/$psname2/" |
  sed -e "s/@psmodule1/$psmodule1/" |
  sed -e "s/@psmodule2/$psmodule2/" |
  sed -e "s/@psmodule3/$psmodule3/" |
  sed -e "s/@psmodule4/$psmodule4/" |
  sed -e "s/@psmodule5/$psmodule5/" |
  sed -e "s^@icon_data_uri^$icon_data_uri^" |
  sed -e "s^@qrcode_data_uri^$qrcode_data_uri^"
}

template=$(dirname $0)/lcd-480x320-template.svg
ipaddress=$(ip addr | grep -e wlan0 -e wlp3s0 | grep inet | grep -Eo "[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+" | head -1)
swversion=v0.2.0+beta99
ampname="Mustang LT40S"
fwversion="firmware 1.0.7"
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

# Generate a mockup
svg_text1=$(generate_svg_text)
echo $svg_text1 > $(dirname $0)/lcd-mockup.svg
echo $svg_text1 | rsvg-convert - -o $(dirname $0)/lcd-mockup.png
echo $svg_text1 | rsvg-convert - --zoom=2.5 -o $(dirname $0)/lcd-mockup-large.png
if [ ! "$?" = 0 ]
then
  echo SVG Text:
  echo $svg_text1
fi

# Generate a splash screen (must be PNG, must be < 13k to display on Balena)
template=$(dirname $0)/lcd-480x320-splash.svg
svg_text2=$(generate_svg_text)
echo $svg_text2 > $(dirname $0)/lcd-startup.svg
echo $svg_text2 | rsvg-convert - -o $(dirname $0)/lcd-startup.png
if [ ! "$?" = 0 ]
then
  echo SVG Text:
  echo $svg_text2
fi

# BalenaCloud requires the startup image to be 13.09kb or less
# so we need to minimize that image
optipng $(dirname $0)/lcd-startup.png --clobber -o8 -out $(dirname $0)/lcd-startup-min.png
ls -l $(dirname $0)/*.png

