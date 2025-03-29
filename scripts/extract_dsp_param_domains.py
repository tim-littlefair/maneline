#! python3
# extract_dsp_param_domains.py
# Script to parse JSON files exported by FHAU and, for each DSP unit parameter
# list values it can take on.

import json
import sys
import os

class ParamDomainDb:
    def __init__(self): 
        self.param_domains= {}

    def parse_file(self,fn):
        preset = json.load(open(fn))
        for node in preset["audioGraph"]["nodes"]:
            for attributeName in node["dspUnitParameters"]:
                attributeValue = node["dspUnitParameters"][attributeName]
                paramKey1 = (attributeName,"")
                paramKey2 = (attributeName,node["FenderId"])
                for pk in ( paramKey1, paramKey2 ):
                    if (
                        pk in self.param_domains and
                        attributeValue not in self.param_domains[pk]
                    ) :
                        self.param_domains[pk] += [ attributeValue ]
                    else:
                        self.param_domains[pk] = [ attributeValue ]
    
    def dump(self):
        for pk in sorted(self.param_domains.keys()):
            value_list = self.param_domains[pk]
            try:
                print(pk, sorted(value_list))
            except TypeError:
                print(pk, "unsortable: ", value_list)


if __name__ == "__main__":
    pdd = ParamDomainDb()
    dir=sys.argv[1]
    for f in os.listdir(dir):
        if not "raw" in f:
            continue
        pdd.parse_file(os.path.join(dir,f))
    pdd.dump()


