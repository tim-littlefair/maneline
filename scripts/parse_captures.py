#! python3

# parse_captures.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)
# Script to extract traffic between one of the Fender Tone
# applications and a Fender device.
# As of this version, the script can parse wireshark format
# dumps.
# Supported app/device pairs tested to date
# + Fender Tone for Android driving Mustang Micro Plus (using Developer
#   Mode Bluetooth Snoop on Android)
# + Fender Tone Desktop for macOS driving Mustang LT40S (using Wireshark
#   running on macOS)
# + Fender Tone Desktop for Windows driving Mustang LT40S (using
#   Wireshark running on Windows Virtual Box VM)

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
        for fn in ( 'btsnoop_hci.log.last', 'btsnoop_hci.log'):
            try:
                capture_bytes = brzip.read(f"FS/data/misc/bluetooth/logs/{fn}")
                retval += [ capture_bytes ]
            except KeyError:
                print(f"{bugreport_path} does not contain {fn}",file=sys.stderr)
    if len(retval)==0:
        print(f"No Bluetooth logs found in {fn}", file=sys.stderr)
        print(f"Is this file an ADB bugreport?", file=sys.stderr)
        print(f"Was Bluetooth snooping turned on in Developer settings?", file=sys.stderr)
    return retval

def extract_logstreams_from_capture(capture_path):
    retval = []
    if capture_path.endswith(".zip"):
        retval += [
            (capture_bytes, tshark_utils.ADB_BLUETOOTH_CAPTURE)
            for capture_bytes in extract_logstreams_from_adb_bugreport(capture_path)
        ]
    elif capture_path.endswith(".pcapng"):
        capture_bytes = open(capture_path,mode="rb").read()
        retval += [
            ( capture_bytes, tshark_utils.WIRESHARK_USB_CAPTURE )
        ]
    for capture_bytes, _ in retval:
        time_range = tshark_utils.extract_time_range_from_capture(capture_bytes)
        tshark_utils.dump_capture(capture_bytes, time_range, "json")
        # print(f"{brzippath}:{fn} time range: {time_range}")
        tshark_utils.dump_capture(
            capture_bytes,
            outpath+"/"+time_range+".json",
            wshark_dump_type="json"
        )
    return retval


def dump_requests_and_responses(capture_bytes, capture_type):
    global req_seq, rsp_seq, message, message_id
    lb_lines = tshark_utils.extract_messages_from_capture(capture_bytes, capture_type)
    for lb_line in lb_lines:
        fields = lb_line.split(",")
        if len(fields)<2:
            continue
        elif len(fields[1])==0:
            continue
        elif fields[1]=="3500050a03c20100":
            # app -> mpp ping
            continue
        # If we get to this point fields[0] gives us the destination, and fields[1] contains a packet
        # which has been transported
        # We assign a message id which also tells us the direction - requests from the Plug/Tone app
        # to the Mustang get a single part message id, but the Mustang can send more than one
        # response after a request, so responses get a two part message id
        if fields[0] == "Mustang Micro Plus" or fields[0].startswith("1"):
            if message_id is None:
                req_seq += 1
                rsp_seq = None
                message_id = ( req_seq, )
            #assert len(message_id)==1
        else:
            assert req_seq is not None
            if rsp_seq is None:
                rsp_seq=1
            message_id = (req_seq,rsp_seq)
        # print(f"{message_id}" + fields[1])
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
                    msg_basename=".".join(["%02d"%(i,) for i in message_id])
                    if len(message_id)==1:
                        msg_basename += '.'
                    print(f"Saving message {msg_basename}")
                    msg_raw_pb_parse, msg_bytes = None, None
                    try:
                        msg_raw_pb_parse, msg_bytes = pb_utils.parse_message_frame(message)
                    except pb_utils.IncompleteFrameException as e:
                        msg_raw_pb_parse = "Incomplete frame - no protobuf parse attempted"
                        msg_bytes = e.incomplete_frame_bytes
                    msg_path_prefix = f"{outpath}/{msg_basename}"
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
        logbyte_list = extract_logstreams_from_capture(snoop_path)
    try:
        req_num = 0, 0
        start_reqseq = req_seq
        for lb in logbyte_list:
            dump_requests_and_responses(lb[0],lb[1])
            open(
                outpath + f"/requests_{start_reqseq}-{req_seq}.csv","wt"
            ).write(tshark_utils.extract_csv(lb[0],lb[1]))
            start_reqseq=req_seq
    except AssertionError as e:
        print(req_seq, rsp_seq, message_id, message)
        traceback.print_exception(e,limit=4)



