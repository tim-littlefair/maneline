#! python3

# parse_adb_bugreport_btsnoops.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)
# Script to extract Bluetooth traffic between an Android Phone
# running the Fender Tone application and a Fender Mustang Micro Plus

import subprocess
import sys
import zipfile

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
                ts_begin, ts_end = extract_bounds_from_btsnoop_log_bytes(btsnoop_log_bytes)
                print(f"{brzippath}:{fn} starts at {ts_begin} and ends at {ts_end}")
                retval += [ btsnoop_log_bytes ]
            except KeyError:
                print(f"{brzipppath} does not contain {fn}")
    return retval

def extract_bounds_from_btsnoop_log_bytes(btsnoop_log_bytes):
    tshark_output_lines = _run_tshark_with_args(
        btsnoop_log_bytes,
        ["-Tfields", "-eframe.time_epoch"]
    ).split("\n")
    return tshark_output_lines[0], tshark_output_lines[-2]

def extract_values_from_btsnoop_log_bytes(btsnoop_log_bytes):
    tshark_output_lines = _run_tshark_with_args(
        btsnoop_log_bytes,
        ["-Tfields", "-ebthci_acl.dst.name", "-ebtatt.value"]
    ).split("\n")
    return tshark_output_lines


if __name__ == "__main__":
    for brzippath in sys.argv[1:]:
        logbyte_list = extract_logstreams_from_bugreport(brzippath)
    msg_len_histogram = {}
    for lb in logbyte_list:
        lb_lines = extract_values_from_btsnoop_log_bytes(lb)
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
    for k in sorted(msg_len_histogram.keys()):
        print(k,msg_len_histogram[k])


