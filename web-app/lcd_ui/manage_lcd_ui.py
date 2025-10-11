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

from watchdog.observers import Observer
from watchdog.events import PatternMatchingEventHandler
from watchdog.events import FileSystemEventHandler

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
        icon_data_uri, qrcode_data_uri,
        run_dir
):
    svg_text = svg_template
    svg_text = svg_text.replace("@ipaddress",ipaddr)
    for key in device_details.keys():
        value = device_details.get(key,"?")
        if value == "swversion" and len(value)>14:
            value=value[0:14]
        svg_text = svg_text.replace("@"+key,value)

    for key in preset_details.keys():
        if key == "psslot":
            svg_text = svg_text.replace("##",preset_details.get(key,"?"))
        else:             
            svg_text = svg_text.replace("@"+key,str(preset_details.get(key,"?")))
    svg_text = svg_text.replace("@icon_data_uri",icon_data_uri)
    svg_text = svg_text.replace("@qrcode_data_uri",qrcode_data_uri)
    session_dir = find_latest_session_dir(run_dir)
    svg_fn = f"{session_dir}/maneline_lcd-{time.time():.3f}.svg"
    open(svg_fn,"w").write(svg_text)
    return svg_fn

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

class PresetUpdatedHandler(PatternMatchingEventHandler):
    def __init__(self, run_dir, ipaddr, display_method):
        super().__init__(
            patterns = [ "./preset-details.json" ],
            ignore_directories=True
        )
        self.run_dir = run_dir
        self.ipaddr = ipaddr
        self.display_method = display_method

    def on_closed(self,event):
        update_lcd_ui(self.run_dir, self.ipaddr, display_method)


def update_lcd_ui(run_dir, ipaddr, display_method):
    device_details = json.load(open(os.path.join(run_dir, "amp-details.json")))
    preset_details = json.load(open(os.path.join(run_dir, "preset-details.json")))
    icon_data_uri = generate_icon_data_uri()
    qrcode_data_uri = generate_qrcode_data_uri(ipaddr)
    svg_fn=generate_svg(
        svg_template_str, ipaddr,
        device_details, preset_details,
        icon_data_uri, qrcode_data_uri,
        run_dir
    )
    if display_method is None:
        pass
    elif display_method == "--web":
        webbrowser.open(svg_fn)
    elif display_method.startswith("--fb"):
        png_fn=svg_fn.replace(".svg",".png")
        subprocess.run(
            f"/usr/bin/rsvg-convert -w 480 -h 320 {svg_fn} -o {png_fn}",
            shell=True
        )
        dev_paths = None
        if display_method == "--fb-both":
            dev_paths = [ "/dev/fb0", "/dev/fb1" ]
        else:
            dev_paths=[ display_method.replace("--","/dev/") ]
        for dev_path in dev_paths:
            assert os.path.exists(dev_path)
            subprocess.run(
                f"/usr/bin/fbi -a -T 1 -noverbose -d {dev_path} {png_fn}",
                shell=True
            )
    else:
        print(f"Unexpected display method: {display_method}", file=sys.stderr)
        exit(1)

if __name__ == "__main__":
    try:
        run_dir=None
        display_method=None
        try:
            run_dir=sys.argv[1]
            sys.argv.pop(1)
        except IndexError:
            run_dir="."
        try:
            display_method=sys.argv[1]
            if display_method=="--fbX":
                if os.path.exists("/dev/fb1"):
                    display_method="--fb1"
                elif os.path.exists("/dev/fb0"):
                    display_method="--fb0"
                else:
                    display_method=None
            display_method=sys.argv.pop(1)
        except IndexError:
            pass

        lcd_ui_dir=f"{run_dir}/lcd_ui"
        svg_template_str = open(
            os.path.join(lcd_ui_dir,"lcd-480x320-template.svg"),
            "rt"
        ).read()
        ipaddr=getNetworkIp()
        session_dir = find_latest_session_dir(run_dir)

        preset_details_observer = Observer()
        preset_details_observer.schedule(
            PresetUpdatedHandler(run_dir, ipaddr, display_method),
            f"{run_dir}",recursive=True,
        )
        preset_details_observer.start()
        #update_lcd_ui(run_dir, ipaddr, display_method)
        try:
            while True:
                time.sleep(60)
        except KeyboardInterrupt:
            pass
        preset_details_observer.stop()
        preset_details_observer.join()


    except:
        traceback.print_exc()

