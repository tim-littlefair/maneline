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
            connections = ps["audioGraph"]["connections"]
            nodes = ps["audioGraph"]["nodes"]
            cxn_order = []
            for connection in connections:
                input = connection["input"]
                output = connection["output"]
                cxn_order += [
                    input["nodeId"][0] + str(input["index"]) +
                    output["nodeId"][0] + str(output["index"])
                ]
            node_order = ""
            for node in nodes:
                nodeId = node["nodeId"][0]
                if node["FenderId"] != "DUBS_Passthru":
                    nodeId=nodeId.upper()
                node_order += nodeId
            print(node_order, ".".join(cxn_order),f)
        else:
            pass


if __name__ == "__main__":
    scan_dir(sys.argv[1])
