#! python3
# generate_lcd_ui.py
# Script to generate and display an SVG file which 
# exposes the IP address, Mustang amplifier connectivity state
# and current selected preset to the user.

import sys
import json
import os
import subprocess
import time
import traceback
import webbrowser

# Get local ip address
# ref: https://stackoverflow.com/a/23822431
def getNetworkIp():
    import socket
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    s.connect(('<broadcast>', 12345))  # 12345 is random port. 0 fails on Mac.
    return s.getsockname()[0]

def generate_svg(
        svg_template, ipaddr,
        device_details, preset_details,
        icon_data_uri, qrcode_data_uri
):
    svg_text = svg_template
    svg_text = svg_text.replace("@ipaddress",ipaddr)
    for key in device_details.keys():
        svg_text = svg_text.replace("@"+key,device_details.get(key,"?"))
    for key in preset_details.keys():
        if key == "psslot":
            svg_text = svg_text.replace("##",preset_details.get(key,"?"))
        else:             
            svg_text = svg_text.replace("@"+key,str(preset_details.get(key,"Passthru")))
    svg_text = svg_text.replace("@icon_data_uri",icon_data_uri)
    svg_text = svg_text.replace("@qrcode_data_uri",qrcode_data_uri)
    svg_fn = f"maneline_lcd-{time.time()}.svg"
    open(svg_fn,"w").write(svg_text)
    webbrowser.open(svg_fn)
    pass

def find_latest_session_dir(run_dir):
    session_dirs = [
        d for d in sorted(os.listdir("."))
        if d.startswith("session_")
    ]
    return session_dirs[-1]

def generate_icon_data_uri():
    generation_outcome = subprocess.run(
        f"cat {run_dir}/web_ui/_static/maneline-icon-512x512.png|base64 --wrap=0",
        shell=True, capture_output=True, text=True
    )
    icon_data_base64=generation_outcome.stdout
    return f"data:image/png;base64,{icon_data_base64}"

def generate_qrcode_data_uri(ipaddr):
    generation_outcome = subprocess.run(
        f"zint -b QRCODE -d http://{ipaddr}:8080 --filetype=PNG --direct| base64 --wrap=0",
        shell=True, capture_output=True, text=True
    )
    qrcode_data_base64=generation_outcome.stdout
    return f"data:image/png;base64,{qrcode_data_base64}"

if __name__ == "__main__":
    try:
        run_dir=None
        try:
            run_dir=sys.argv[1]
        except IndexError:
            run_dir="."
        lcd_ui_dir=f"{run_dir}/lcd_ui"
        svg_template_str = open(
            os.path.join(lcd_ui_dir,"lcd-480x320-template.svg"),
            "rt"
        ).read()
        ipaddr=getNetworkIp()
        session_dir = find_latest_session_dir(run_dir)
        device_details = json.load(open(os.path.join(run_dir,"amp-details.json")))
        preset_details = json.load(open(os.path.join(run_dir,"preset-details.json")))
        icon_data_uri = generate_icon_data_uri()
        qrcode_data_uri = generate_qrcode_data_uri(ipaddr)
        generate_svg(
            svg_template_str, ipaddr,
            device_details, preset_details,
            icon_data_uri, qrcode_data_uri
        )

    except:
        traceback.print_exc()

r"""

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

"""