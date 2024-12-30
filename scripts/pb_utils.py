#! python3

# protobuf_utils.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)

import subprocess

def _run_protoc_with_args(protobuf_bytes,protoc_args):
    cli_args = [ "protoc", ]
    cli_args += protoc_args
    completed_process = subprocess.run(
        args = cli_args,
        input = protobuf_bytes,
        capture_output = True,
        timeout = 10
    )
    if completed_process.stderr:
        print("Protobuf error: " + str(completed_process.stderr,"utf-8"))
    else:
        assert completed_process.returncode==0
        assert len(completed_process.stderr)==0
    return str(completed_process.stdout, "UTF-8")

def parse(protobuf_raw_bytes):
    return _run_protoc_with_args(protobuf_raw_bytes,[ "--decode_raw" ] )
