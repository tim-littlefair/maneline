#! python3

# This script is intended to unpack certain protobuf messages
# captured from snooping the BLE connection between the Android
# version of FenderTone and a Mustang Micro Plus device.

# The messages the script applies to are the responses to
# requests for preset definitions.  The protobuf wraps
# lz4-compressed single line JSON, the script decompresses
# the lz4, parses the JSON into a multi-level Python dictionary
# object and reserializes the dictionary back into
# pretty-printed JSON.

import json
import os
import sys
import binascii

import lz4.block

def process_preset_response(fn_in):
    try:
        if "command" in fn_in:
            return
        _PB_PREFIX_LENGTH = 8
        input_stream = open(fn_in,"rb")
        pb_prefix = input_stream.read(_PB_PREFIX_LENGTH)
        print(str(binascii.b2a_hex(pb_prefix),"utf-8"))
        if len(pb_prefix)<_PB_PREFIX_LENGTH:
            return
        if pb_prefix[0:2] != b"\x08\x02":
            return
        lz4_json = input_stream.read()
        compact_json = str(lz4.block.decompress(lz4_json,uncompressed_size=10240),"utf-8")
        #print(compact_json)
        preset_dict = json.loads(compact_json)
        pretty_json = json.dumps(preset_dict,indent=4)
        fn_out = fn_in.replace(".bin",".pretty_preset.json")
        assert fn_out != fn_in
        print(fn_out)
        print(pretty_json)
        with open(fn_out,"wt") as output_stream:
            output_stream.write(pretty_json)
    except:
        print(fn_in)
        print(sys.exc_info()[1])
        pass


if __name__ == "__main__":
    for fn in sys.argv[1:]:
        process_preset_response(fn)
