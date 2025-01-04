#! python3

# parse_captures.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)
# Script to extract traffic between one of the Fender Tone
# applications and a Fender device.
# As of this version, the script can parse wireshark format
# dumps, including .pcapng files captured directly by wireshark
# and Bluetooth snoop dumps recorded on a suitably prepared Android
# device using the 'adb bugreport' command.
#
# Supported app/device pairs tested to date
# + Fender Tone Desktop for macOS driving Mustang LT40S (using Wireshark
#   running on macOS)
# + Fender Tone Desktop for Windows driving Mustang LT40S (using
#   Wireshark running on Windows Virtual Box VM)
# + Fender Tone for Android driving Mustang Micro Plus (using Developer
#   Mode Bluetooth Snoop on Android)

import binascii
import os
import sys
import time
import traceback
import zipfile

from fhau_pylib import pb_utils, tshark_utils, hexdump

def extract_logstreams_from_adb_bugreport(bugreport_path):
    retval=[]
    with zipfile.ZipFile(bugreport_path, "r") as brzip:
        brzip_btlog_dirpath = "FS/data/misc/bluetooth/logs"
        # If an Android phone has debugging enabled, and the 'adb bugreport'
        # command issued on a workstation connected to it, a zip file will
        # be created on the workstation containing various logs from the device.
        # If Settings option 'System/Developer options/Enable Bluetooth HCI Snoop Log'
        # is enabled, the zip file will contain a pair of files under the
        # folder name above - one will contain logs going back
        # for some time (possibly back to most recent occurrence of Bluetooth
        # being enabled), and one containing logs going back a few minutes.
        # Both logs are binary, but can be opened by Wireshark or the
        # Wireshark command line utility tshark.
        # The settings option name above is taken from a Google Nexus 5X
        # running Android 8.1.0 - the option may be differently named
        # or perhaps not available at all on phones from other vendors
        # or running other Android versions.
        for fn in ( 'btsnoop_hci.log.last', 'btsnoop_hci.log'):
            try:
                capture_bytes = brzip.read(f"{brzip_btlog_dirpath}/{fn}")
                retval += [ [ capture_bytes, fn ], ]
            except KeyError:
                print(f"{bugreport_path} does not contain {fn}",file=sys.stderr)
    if len(retval)==0:
        print(f"No Bluetooth logs found in {fn}", file=sys.stderr)
        print(f"Is this file an ADB bugreport?", file=sys.stderr)
        print(f"Was Bluetooth snooping turned on in Developer options?", file=sys.stderr)
    return retval

def extract_logstreams_from_capture(capture_path):
    retval = []
    if capture_path.endswith(".zip"):
        retval += [
            [capture_bytes, tshark_utils.ADB_BLUETOOTH_CAPTURE, f"{capture_path}:{zip_item_path}"]
            for capture_bytes, zip_item_path in extract_logstreams_from_adb_bugreport(capture_path)
        ]
    elif capture_path.endswith(".pcapng"):
        capture_bytes = open(capture_path,mode="rb").read()
        retval += [
            [ capture_bytes, tshark_utils.WIRESHARK_USB_CAPTURE, capture_path ]
        ]
    for capture_details in retval:
        capture_bytes, _, capture_path_extended = capture_details
        time_range = tshark_utils.extract_time_range_from_capture(capture_bytes)
        capture_details += [ time_range ]
        if time_range is None:
            print(f"No time range found for capture {capture_path_extended}",file=sys.stderr)
            continue
        print(f"Processing file {capture_path_extended} covering time range {time_range}")
        tshark_utils.dump_capture(
            capture_bytes,
            outpath+"/"+time_range+".json",
            wshark_dump_type="json"
        )
    return retval


def dump_requests_and_responses(capture_bytes, capture_type, time_range):
    global req_seq, rsp_seq, message, message_id
    time_range_dir = outpath + "/" + time_range
    os.makedirs(time_range_dir, exist_ok=True)
    lb_lines = tshark_utils.extract_messages_from_capture(capture_bytes, capture_type)
    for lb_line in lb_lines:
        fields = lb_line.split(",")
        if len(fields)<2:
            continue
        elif len(fields[1])==0:
            continue
        elif (
            fields[1]=="3500050a03c20100"
            or
            fields[1].startswith("35070800ca0c020801")
        ):
            # app -> mpp ping
            continue
        # If we get to this point fields[0] gives us the destination, and fields[1] contains a packet
        # which has been transported
        # We assign a message id which also tells us the direction - commands from the Plug/Tone app
        # to the Mustang get a single part message id, but the Mustang can send zero or more
        # reports triggered by a single command, so reports get a two part message id with the first
        # part reflecting the most recent command send from the app to the device.
        if (
            fields[0] == "Mustang Micro Plus" # Bluetooth HCI transport - Mustang Micro Plus
            or
            fields[0].startswith("1") # USB HID transport - LT40S
        ):
            if message_id is None:
                req_seq += 1
                rsp_seq = None
                message_id = ( req_seq, )
        else:
            assert req_seq is not None
            if rsp_seq is None:
                rsp_seq=1
            message_id = (req_seq,rsp_seq)
        packet_bytes = binascii.a2b_hex(fields[1])
        last_packet = False
        while True:
            if packet_bytes[0]==0x33:
                # A new multi-packet message is starting
                assert message is None
                message = b''
            elif packet_bytes[0]==0x34:
                # A multi-packet message is continuing and does not end with this packet
                pass
            elif packet_bytes[0]==0x35:
                if message is None:
                    message = b''
                last_packet = True
            elif packet_bytes[0]==0:
                # LT40S response packets start with a leading 0x00
                # Strip the leading byte and continue
                packet_bytes = packet_bytes[1:]
                continue
            # if we get here we probably have a packet
            length_index = None
            if capture_type == tshark_utils.ADB_BLUETOOTH_CAPTURE:
                assert packet_bytes[1]==0x00
                length_index = 2
            else:
                length_index = 1
            #assert packet_bytes[length_index]==len(packet_bytes)-(length_index+1)
            if message is None:
                message = b''
            message += packet_bytes[length_index+1:]
            #print(f"{message_id}" + str(binascii.b2a_hex(message),'utf-8'))
            if last_packet is False:
                pass
            else:
                if(len(message)>2):
                    msg_type = None
                    msg_basename=".".join(["%02d"%(i,) for i in message_id])
                    if len(message_id)==1:
                        msg_type = "command"
                        msg_basename += '-command'
                    else:
                        msg_type = "report"
                    print(f"Saving {msg_type} {msg_basename}")
                    msg_raw_pb_parse, msg_bytes = None, None
                    try:
                        msg_raw_pb_parse, msg_bytes = pb_utils.parse_message_frame(message)
                    except pb_utils.IncompleteFrameException as e:
                        msg_raw_pb_parse = "Incomplete frame - no protobuf parse attempted"
                        msg_bytes = e.incomplete_frame_bytes
                    msg_path_prefix = f"{time_range_dir}/{msg_basename}"
                    open(msg_path_prefix + ".bin","wb").write(msg_bytes)
                    open(msg_path_prefix + ".hexdump","wt").write(hexdump.hexdump(msg_bytes))
                    print(msg_raw_pb_parse,file=open(msg_path_prefix + ".raw_pb_parse.txt","wt"))

                message=None
                message_id = None
                if rsp_seq is not None:
                    rsp_seq+=1
            break

if __name__ == "__main__":
    # TODO:
    # I'm not proud of these globals, should refactor into a class with
    # appropriate state when I understand the shape of this better
    global outpath
    outpath = "_work/pabb_%d" % ( int(time.time()), )
    global req_seq, rsp_seq
    req_seq, rsp_seq = 0,0
    global message, message_id
    message = None
    message_id = None

    os.makedirs(outpath)
    print(f"Dumped data will be in {outpath}")

    for snoop_path in sys.argv[1:]:
        req_seq=0
        logbyte_list = extract_logstreams_from_capture(snoop_path)
        try:
            for lb in logbyte_list:
                req_num = 0, 0
                start_reqseq = req_seq
                if lb[3] is None:
                    continue
                dump_requests_and_responses(lb[0],lb[1],lb[3])
                open(
                    outpath + f"/{lb[3]}/requests_{start_reqseq}-{req_seq}.csv","wt"
                ).write(tshark_utils.extract_csv(lb[0],lb[1]))
                start_reqseq=req_seq
        except AssertionError as e:
            print(req_seq, rsp_seq, message_id, message)
            traceback.print_exception(e,limit=4)



