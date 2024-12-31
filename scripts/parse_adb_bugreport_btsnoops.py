#! python3

# parse_adb_bugreport_btsnoops.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)
# Script to extract Bluetooth traffic between an Android Phone
# running the Fender Tone application and a Fender Mustang Micro Plus

import binascii
import calendar
import os
import subprocess
import re
import sys
import time
import traceback
import zipfile

from fhau_pylib import pb_utils

_TIMESTAMP_PATTERN = re.compile(r"(\w+) (\d+), 20(\d+) (\d+:\d+:\d+)\.\d+ (\w+)")

def _run_tshark_with_args(btsnoop_log_bytes,tshark_args):
    cli_args = [ "tshark", "-r", "-" ]
    cli_args += tshark_args
    completed_process = subprocess.run(
        args = cli_args,
        input = btsnoop_log_bytes,
        capture_output = True,
        timeout = 10
    )
    assert completed_process.returncode==0
    assert len(completed_process.stderr)==0
    return str(completed_process.stdout, "UTF-8")


def extract_logstreams_from_bugreport(brzippath):
    retval = []
    with zipfile.ZipFile(brzippath,"r") as brzip:
        for fn in ( 'btsnoop_hci.log.last', 'btsnoop_hci.log'):
            try:
                btsnoop_log_bytes = brzip.read(f"FS/data/misc/bluetooth/logs/{fn}")
                dump_btsnoop_log_bytes(btsnoop_log_bytes,fn,"json")
                time_range = extract_time_range_from_btsnoop_log_bytes(btsnoop_log_bytes)
                print(f"{brzippath}:{fn} time range: {time_range}")
                retval += [ btsnoop_log_bytes ]
                json = dump_btsnoop_log_bytes(
                    btsnoop_log_bytes,
                    time_range,
                    wshark_dump_type="json"
                )
            except KeyError:
                print(f"{brzipppath} does not contain {fn}")
    return retval

def dump_btsnoop_log_bytes(btsnoop_log_bytes,fn,wshark_dump_type):
    global outpath
    tshark_dump_utf8 = _run_tshark_with_args(
        btsnoop_log_bytes,
        ["-T", wshark_dump_type]
    )
    open(outpath+"/"+fn+"."+wshark_dump_type,"wt").write(tshark_dump_utf8)

def extract_time_range_from_btsnoop_log_bytes(btsnoop_log_bytes):
    tshark_output_lines = _run_tshark_with_args(
        btsnoop_log_bytes,
        ["-Tfields", "-eframe.time"]
    ).split("\n")
    begin_match = _TIMESTAMP_PATTERN.search(tshark_output_lines[0]);
    run_date = begin_match.group(3) + begin_match.group(1) + begin_match.group(2)
    for i in range(1,13):
        run_date=run_date.replace(calendar.month_abbr[i],"%02d"%(i,))
    run_begin_tod = begin_match.group(4).replace(":", "")
    run_tz = begin_match.group(5)
    end_match = _TIMESTAMP_PATTERN.search(tshark_output_lines[-2]);
    run_end_tod = end_match.group(4).replace(":", "")
    return run_date + "_" + run_begin_tod + "-" + run_end_tod + "_" + run_tz

def extract_values_from_btsnoop_log_bytes(btsnoop_log_bytes):
    tshark_output_lines = _run_tshark_with_args(
        btsnoop_log_bytes,
        ["-Tfields", "-ebthci_acl.dst.name", "-ebtatt.value"]
    ).split("\n")
    return tshark_output_lines

def dump_requests_and_responses(btsnoop_log_bytes, outdir, msg_len_histogram, req_num):
    global req_seq, rsp_seq, message, message_id
    lb_lines = extract_values_from_btsnoop_log_bytes(btsnoop_log_bytes)
    for lb_line in lb_lines:
        fields = lb_line.split("\t")
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
        if fields[0] == "Mustang Micro Plus":
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
        print(f"{message_id}" + fields[1])
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
            assert packet_bytes[1]==0x00
            assert packet_bytes[2]==len(packet_bytes)-3
            if message is None:
                message = b''
            message += packet_bytes[3:]
            print(f"{message_id}" + str(binascii.b2a_hex(message),'utf-8'))
            if last_packet is False:
                pass
            else:
                if(len(message)>2):
                    print(pb_utils.parse(message))
                message=None
                message_id = None
                if rsp_seq is not None:
                    rsp_seq+=1
            break
            value_len = len(fields[1])
            prv_len_frequency = msg_len_histogram.get(value_len,0)
            msg_len_histogram[value_len] = prv_len_frequency + 1



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

    for brzippath in sys.argv[1:]:
        logbyte_list = extract_logstreams_from_bugreport(brzippath)

    try:
        msg_len_histogram = {}
        req_num = 0, 0
        for lb in logbyte_list:
            dump_requests_and_responses(lb,outpath, msg_len_histogram, req_num)
        for k in sorted(msg_len_histogram.keys()):
            print(k,msg_len_histogram[k])
    except AssertionError as e:
        print(req_seq, rsp_seq, message_id, message)
        traceback.print_exception(e,limit=4)



