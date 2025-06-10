#! python3

# Script to analyze the ordering of the two subtrees of the audioGraph tree
# in raw JSON preset files returned by a Mustang device.

import json
import os
import sys

def scan_dir(dir):
    for f in sorted(os.listdir(dir)):
        if f.endswith(".raw_preset.json"):
            ps = json.load(open(os.path.join(dir,f)))

            # root_order tells us the order of keys at the
            # root of the JSON tree.
            # This order has no meaning, but may be a clue to
            # where/how the preset was last edited (i.e. whether
            # it is
            # + a default preset unmodified since the firmware
            #   state was last factory preset; or
            # + a preset which has been edited on the device
            # + a preset which has been imported using FenderTone
            # )
            root_order = "".join([key[0] for key in ps.keys()])
            nodes = ps["audioGraph"]["nodes"]
            node_order = ""
            for node in nodes:
                nodeId = node["nodeId"][0]
                if node["FenderId"] != "DUBS_Passthru":
                    nodeId=nodeId.upper()
                node_order += nodeId
            print(root_order,node_order,f)
        else:
            pass


if __name__ == "__main__":
    scan_dir(sys.argv[1])
