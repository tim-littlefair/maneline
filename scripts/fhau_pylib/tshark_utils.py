#! python3

# parse_captures.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)
# COPYRIGHT: 
# To the extent possible, the intent of the author Tim Littlefair 
# is that this script should become part of the public domain.

# Library of functions useful in performing extractions from
# Wireshark-format dumps using the Wireshark CLI tool tshark.

import calendar
import re
import subprocess
import sys

class TsharkExtractionException(Exception):
    def __init__(self, exception_message, ):
        super(TsharkExtractionException, self).__init__(exception_message)

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

def dump_capture(btsnoop_log_bytes, dump_path, wshark_dump_type):
    global outpath
    tshark_dump_utf8 = _run_tshark_with_args(
        btsnoop_log_bytes,
        ["-T", wshark_dump_type]
    )
    open(dump_path,"wt").write(tshark_dump_utf8)

def extract_time_range_from_capture(capture_bytes):
    tshark_output_lines = _run_tshark_with_args(
        capture_bytes,
        ["-Tfields", "-eframe.time"]
    ).split("\n")
    begin_match = _TIMESTAMP_PATTERN.search(tshark_output_lines[0])
    if begin_match is None:
        exception_message = f"\n".join([
            "Failed to find begin timestamp in line:",
            tshark_output_lines[0]
        ])
        print(exception_message,file=sys.stderr)
        # raise TsharkExtractionException(exception_message)
        return None
        #raise TsharkExtractionException(f"\n".join([
        #    "Failed to find begin timestamp in line:",
        #    tshark_output_lines[0]
        #]))
    run_date = begin_match.group(3) + begin_match.group(1) + begin_match.group(2)
    for i in range(1,13):
        run_date=run_date.replace(calendar.month_abbr[i],"%02d"%(i,))
    run_begin_tod = begin_match.group(4).replace(":", "")
    run_tz = begin_match.group(5)
    end_match = _TIMESTAMP_PATTERN.search(tshark_output_lines[-2]);
    if end_match is None:
        exception_message = f"\n".join([
            "Failed to find end timestamp in line:",
            tshark_output_lines[0]
        ])
        print(exception_message,file=sys.stderr)
        # raise TsharkExtractionException(exception_message)
        return None
    run_end_tod = end_match.group(4).replace(":", "")
    return run_date + "_" + run_begin_tod + "-" + run_end_tod + "_" + run_tz

_CAPTURE_MESSAGE_FIELDS = {
    ADB_BLUETOOTH_CAPTURE: "bthci_acl.dst.name,btatt.value",
    WIRESHARK_USB_CAPTURE: "usb.dst,usbhid.data",
}

def extract_messages_from_capture(btsnoop_log_bytes, field_list):
    if field_list in _CAPTURE_MESSAGE_FIELDS.keys():
        field_list = _CAPTURE_MESSAGE_FIELDS[field_list]
    tshark_args = [ "-Tfields", "-Eseparator=," ]
    tshark_args += [
        "-e"+ field_name for field_name in field_list.split(",")
    ]
    tshark_output_lines = _run_tshark_with_args(
        btsnoop_log_bytes,
        tshark_args
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