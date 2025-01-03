#! python3

# tshark_utils.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)
# Library of functions useful in performing extractions from
# Wireshark-format dumps using the Wireshark CLI tool tshark.

import calendar
import subprocess
import re

_TIMESTAMP_PATTERN = re.compile(r"(\w+)\s+(\d+), 20(\d+) (\d+:\d+:\d+)\.\d+ (\w+)")

ADB_BLUETOOTH_CAPTURE = "ADB_BLUETOOTH_CAPTURE"
WIRESHARK_USB_CAPTURE = "WIRESHARK_USB_CAPTURE"
def _run_tshark_with_args(btsnoop_log_bytes,tshark_args):
    cli_args = [ "tshark", "-r", "-" ]
    cli_args += tshark_args
    completed_process = subprocess.run(
        args = cli_args,
        input = btsnoop_log_bytes,
        capture_output = True,
        timeout = 10
    )
    if len(completed_process.stderr)>0:
        print(str(completed_process.stderr,"UTF-8"))
    assert completed_process.returncode==0
    assert len(completed_process.stderr)==0
    return str(completed_process.stdout, "UTF-8")

def dump_btsnoop_log_bytes(btsnoop_log_bytes,dump_path,wshark_dump_type):
    global outpath
    tshark_dump_utf8 = _run_tshark_with_args(
        btsnoop_log_bytes,
        ["-T", wshark_dump_type]
    )
    open(dump_path,"wt").write(tshark_dump_utf8)

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


_CAPTURE_DEFAULT_FIELDS = {
    ADB_BLUETOOTH_CAPTURE: ",".join([
        "frame.number,frame.time_relative",
        "hci_h4.direction,hci_h4.type,bthci_cmd.opcode,bthci_evt.code,bthci_evt.opcode",
        "btatt.opcode,btatt.handle,btatt.value"
    ]),
    WIRESHARK_USB_CAPTURE:
        ",".join([
            "frame.number,frame.time_relative",
            "usb.src,usb.dst,usb.endpoint_address,usb.transfer_type",
            "usbhid.data"
        ]),
}
def extract_csv(btsnoop_log_bytes, field_list):
    if field_list in _CAPTURE_DEFAULT_FIELDS.keys():
        field_list = _CAPTURE_DEFAULT_FIELDS[field_list]
    tshark_args = [ "-Tfields", "-Eseparator=," ]
    tshark_args += [
        "-e"+ field_name for field_name in field_list.split(",")
    ]
    return field_list + "\n" + _run_tshark_with_args(
        btsnoop_log_bytes, tshark_args
    )