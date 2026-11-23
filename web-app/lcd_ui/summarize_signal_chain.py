#! python3

# The purpose of the function(s) in this file is to enable the 
# manage_lcd_unit.py script to convert a preset-details.json file
# saved by the Java CLI app into an array of strings, each of which 
# summarizes the state of a single DSP unit in the signal chain.

import sys
import json
import os
import signal
import subprocess
import time
import traceback
import webbrowser
import math
import sys
from io import StringIO

def _render_param(param_details, param_name):
    if (
        (param_name not in param_details) or 
        (param_name.startswith("other")) or
        ("#" in param_name) or
        (param_name in ( "cab"))
    ):
        return ""
    else:
        param_value = param_details[param_name]
        if param_value is None:
            return ""
        if param_value == "off":
            # noiseGate etc: if a feature is turned off other params
            # related to it are not worth showing
            related_param_names = [ 
                pn for pn in param_details.keys() 
                if pn>param_name and pn.startswith(param_name) 
            ]
            for rpn in related_param_names:
                del param_details[rpn]
        return f" {param_name}={param_value}"

def _filter_least_useful_lines(pslines, max_lines):
    amp_continuation_lines = []
    other_continuation_lines = []
    for line in range(len(pslines)-1,0,-1):
        if "amp: " in pslines[line]:
            amp_continuation_lines += [line]
        elif ": " in pslines[line]:
            other_continuation_lines += [line]
    while len(pslines)>max_lines:
        if len(other_continuation_lines)>0:
            removable_line=other_continuation_lines.pop()
            pslines.remove(pslines[removable_line])
            for i in range(0,len(amp_continuation_lines)):
                if amp_continuation_lines[i]>removable_line:
                    amp_continuation_lines[i]-=1
        elif len(amp_continuation_lines)>0:
            removable_line=amp_continuation_lines.pop()
            pslines.remove(pslines[removable_line])

def summarize_signal_chain(preset_details, max_lines=5, max_line_length=85):
    retval = []
    for du in preset_details["dspUnits"]:
        unit_description_sio = StringIO()
        unit_description_sio.write(f'{du["type"]}:{du["name"]}')
        du_type = du["type"]
        du_name = du["name"]
        del du["type"]
        del du["name"]
        if du_type == "amp":
            # First spit out the params which map to knobs on top
            # (NB 'middle' does not have a physical knob on Mustang I/II or LT series
            # but should clearly have equal prominence to 'treble' and 'bass')
            for param_name in ( "gain", "volume", "treble", "middle", "bass" ):
                unit_description_sio.write(_render_param(du, param_name))
                del du[param_name]
            retval += [ unit_description_sio.getvalue() ]
            # Create a second line for the amp for additional params
            unit_description_sio = StringIO()
            unit_description_sio.write(f'{du_type}:{" "*len(du_name)}')
        while len(du.keys())>0:
            param_name = sorted(du.keys())[0]
            param_rendered = _render_param(du, param_name)
            if len(unit_description_sio.getvalue()) + len(param_rendered) > max_line_length:
                retval += [ unit_description_sio.getvalue() ]
                # Create a second line for the amp for additional params
                unit_description_sio = StringIO()
                unit_description_sio.write(f'{du_type}:{" "*(len(du_name)-1)}')
            unit_description_sio.write(param_rendered)
            del du[param_name]
        retval += [ unit_description_sio.getvalue() ]
    _filter_least_useful_lines(retval,max_lines)
    return retval

if __name__ == "__main__":
    # Interactive test
    preset_details = json.load(open(sys.argv[1]))
    sigchain_summary = summarize_signal_chain(preset_details)
    print(preset_details["psname_full"]+"\n" + "\n".join(sigchain_summary))
    