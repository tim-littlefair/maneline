#! python3
# -*- coding:utf-8 -*-
# See sibling script epaper_support_install.sh for 
# details of what python modules and supporting files
# must be present for this to work

import sys
import os
import subprocess
import logging
import webbrowser

from PIL import Image,ImageDraw,ImageFont

logging.basicConfig(level=logging.INFO)

epd = None
epd_params = None

if "BALENA_SERVICE_NAME" in os.environ:
    try:
        libdir = './lib'
        if os.path.exists(libdir):
            sys.path.append(libdir)
        from waveshare_epd import epd2in13_V4 # type: ignore
        font16 = ImageFont.truetype(os.path.join('./pic', 'Font.ttc'), 16)
        font18 = ImageFont.truetype(os.path.join('./pic', 'Font.ttc'), 18)
        font30 = ImageFont.truetype(os.path.join('./pic', 'Font.ttc'), 30)
        epd = epd2in13_V4.EPD()
        epd.init()
        epd_params = ( epd.height, epd.width, font16, font18, font30)

    except ImportError:
        # This may or may not be an error depending on whether the
        # intent of the container's Dockerfile was to support the 
        # optional hardware or not
        logging.info("Failed to import waveshare_epd subpackage")
else:
    # Script is running on a development rig or other non-Balena 
    # environment
    pass

if epd_params is None:
        font16 = ImageFont.load_default(16)
        font18 = ImageFont.load_default(18)
        font30 = ImageFont.load_default(30)
        epd_params = ( 250, 122, font16, font18, font30, )
        pass    

def get_ipaddr(ifnames = ( "wlan0", "wlp3s0", "eth0", ) ):
    for ifname in ifnames:
        cmd = "|".join([
            f"ip addr show dev {ifname}",
            "grep 'inet '", 
            "grep --only-matching -E [0-9]+",
            "head -4",
            "paste -s -d."
        ])
        ip_addr_outcome = subprocess.run(
            cmd, shell=True, capture_output=True, text=True
        )
        ipaddr = ip_addr_outcome.stdout.strip()
        if ( 
             ip_addr_outcome.returncode == 0 and 
             len(ip_addr_outcome.stdout.strip())>0
        ):
            logging.info(f"IP address {ipaddr} found for interface {ifname}")
            return ipaddr
        else:
            logging.debug(
                f"'{cmd}' returned {ip_addr_outcome.returncode} with output {ipaddr}"
            )
    raise RuntimeError("IPv4 address not found for any of the supported interfaces")

def generate_url_qrcode(ipaddr,qrcode_filepath="./url_qrcode.png"):
    zint_outcome = subprocess.run(
        f"zint --verbose -b QRCODE --scale=2.0 -d http://{ipaddr}:8080 -o {qrcode_filepath}",
        shell=True, capture_output=True, text=True)
    assert zint_outcome.returncode == 0
    return qrcode_filepath

def generate_url_image(ipaddr,qrcode_filepath="./url_qrcode.png"):
    ( height, width, font16, font18, _ ) = epd_params
    epaper_image = Image.new('1', (height, width), 255)
    epaper_draw = ImageDraw.Draw(epaper_image)
    epaper_draw.rectangle((2, 2, 246, 118), outline = 0)
    epaper_draw.text((114, 12), "Maneline", font = font18, fill = 0)
    epaper_draw.text((114, 37), "http://", font = font18, fill = 0)
    #ipaddr = "255.255.255.255" # test value for max width
    epaper_draw.text((114, 62), ipaddr, font = font18, fill = 0)
    epaper_draw.text((114, 87), ":8080", font = font18, fill = 0)
    qrcode_image = Image.open(qrcode_filepath)
    epaper_image.paste(qrcode_image,(11,11,))
    return epaper_image

def generate_status_image(service_status,status_x):
    ( height, width, _,  _, font30 ) = epd_params
    epaper_image = Image.new('1', (height, width), 255)
    epaper_draw = ImageDraw.Draw(epaper_image)
    epaper_draw.rectangle((2, 2, 248, 120), outline = 0)
    epaper_draw.text((65, 21), "Maneline", font = font30, fill = 0)
    epaper_draw.text((status_x, 61), service_status, font = font30, fill = 0)
    return epaper_image

if __name__ == "__main__":
    try:
        epaper_image = None
        if "--starting-up" in sys.argv:
            epaper_image = generate_status_image("starting up",50)
        elif "--shutting-down" in sys.argv:
            epaper_image = generate_status_image("shutting down",30)
        else:
            ipaddr = get_ipaddr()
            generate_url_qrcode(ipaddr)
            epaper_image = generate_url_image(ipaddr)
        if epd is not None:
            epd.displayPartial(epd.getbuffer(epaper_image))
            epd.sleep()
        elif "--show-in-browser" in sys.argv:
            logging.info("Rendering e-paper image")
            epaper_image.save("epaper.png")
            webbrowser.open("epaper.png")

    except IOError as e:
        logging.info(e)
        
    except KeyboardInterrupt:    
        logging.info("ctrl + c:")
        epd2in13_V4.epdconfig.module_exit(cleanup=True)
        exit()
