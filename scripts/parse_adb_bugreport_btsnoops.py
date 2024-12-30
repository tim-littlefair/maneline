#! python3

# parse_adb_bugreport_btsnoops.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)
# Script to extract Bluetooth traffic between an Android Phone
# running the Fender Tone application and a Fender Mustang Micro Plus

import calendar
import os
import subprocess
import re
import sys
import time
import zipfile

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
                    time_range + "_" + fn,
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
        elif fields[0] == "Mustang Micro Plus":
            print(">MPP " + fields[1])
        else:
            print("<MPP " + fields[1])
        value_len = len(fields[1])
        prv_len_frequency = msg_len_histogram.get(value_len,0)
        msg_len_histogram[value_len] = prv_len_frequency + 1



if __name__ == "__main__":

    global outpath
    outpath = "_work/pabb_%d" % ( int(time.time()), )
    os.makedirs(outpath)
    print(f"Dumped data will be in {outpath}")

    for brzippath in sys.argv[1:]:
        logbyte_list = extract_logstreams_from_bugreport(brzippath)

    msg_len_histogram = {}
    req_num = 0
    for lb in logbyte_list:
        dump_requests_and_responses(lb,outpath, msg_len_histogram, req_num)
    for k in sorted(msg_len_histogram.keys()):
        print(k,msg_len_histogram[k])


