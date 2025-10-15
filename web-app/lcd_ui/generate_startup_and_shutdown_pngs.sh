#! /bin/sh
# generate_startup_and_shutdown_pngs.sh
# Script to generate and display an SVG file which 
# exposes the IP address, Mustang amplifier connectivity state
# and current selected preset to the user.

icon_data_base64=$(base64 --wrap=0 < assets/maneline-icon.png)
icon_data_uri="data:image/png;base64,$icon_data_base64"
echo $icon_data_uri|wc -c
template=$(dirname $0)/lcd-480x320-splash.svg

# Generate splashs screens for startup and shutdown
echo Generating startup image
startup_svg_text=$(cat $template | sed -e "s^@icon_data_uri^$icon_data_uri^")
echo $startup_svg_text|wc -c
echo $startup_svg_text | rsvg-convert - -o $(dirname $0)/lcd-startup.png

echo Generating shutdown image
shutdown_svg_text=$(echo $startup_svg_text | sed -e 's^Starting up^Shutting down^')
echo $shutdown_svg_text|wc -c
echo $shutdown_svg_text | rsvg-convert - -o $(dirname $0)/lcd-shutdown.png

# BalenaCloud requires the startup image to be 13.09kb or less
# so we need to minimize that image
echo Creating minimum size startup image
optipng $(dirname $0)/lcd-startup.png --clobber -o8 -out $(dirname $0)/lcd-startup-min.png
ls -l $(dirname $0)/*.png
