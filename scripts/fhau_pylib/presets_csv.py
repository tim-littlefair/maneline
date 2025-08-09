#! python3

# presets_csv.py
# Author: Tim Littlefair (https://github.com/tim-littlefair)
# COPYRIGHT: 
# To the extent possible, the intent of the author Tim Littlefair 
# is that this script should become part of the public domain.

# Wrapper to dump a CSV file containing presets parsed from 
# a Bluetooth snoop file captured during an Android session 
# of FenderTone with Mustang Micro Plus (or any other interoperable
# FMIC device)

# The output of this script should be as similar as possible to the 
# output of the file PresetCsvGenerator.java in the directory
# lib/src/main/java/net/heretical_camelid/fhau/lib/registries

_DATA_LINE_FORMAT = "%4d,%-16s,%-18s,%-18s,%-18s,%-18s,%-18s,%-18s,%-9s\n"
_HEADER_LINE_FORMAT = _DATA_LINE_FORMAT.replace("%4d","%4s")

_preset_csv_filename = None
_preset_csv_stream = None

def open_csv_file(outdir):
    global _preset_csv_filename
    _preset_csv_filename=outdir+"/presets.csv"
    global _preset_csv_stream
    _preset_csv_stream = open(_preset_csv_filename,"w")
    _preset_csv_stream.write(_HEADER_LINE_FORMAT%(
        "slot","name", "stomp","mod","amp","cabsim","delay","reverb","fhau-hash"        
    ))

def preset_dict_value(dict_or_array, key_array):
    # print(dict_or_array,key_array)
    if len(key_array)>1:
        try:
            return preset_dict_value(dict_or_array[key_array[0]],key_array[1:])
        except TypeError:
            return [
                preset_dict_value(d_o_a,key_array[1:])
                for d_o_a in dict_or_array 
                if d_o_a["nodeId"]==key_array[0]
            ][0]
    else:
        value = dict_or_array[key_array[0]]
        alias = value.replace("DUBS_Unknown","")
        alias = alias.replace("ACD_","")
        alias = alias.replace("Fender","")
        alias = alias.replace("GT","")
        alias = alias.replace("BoilerPlate","BlrPlt")
        return alias

def add_preset_line(slot_index, preset_dict):
    assert _preset_csv_stream is not None
    _preset_csv_stream.write(_DATA_LINE_FORMAT%(
        slot_index,
        preset_dict_value(preset_dict,["info","displayName"]),
        preset_dict_value(preset_dict,["audioGraph","nodes", "stomp","FenderId"]),
        preset_dict_value(preset_dict,["audioGraph","nodes", "mod","FenderId"]),
        preset_dict_value(preset_dict,["audioGraph","nodes", "amp","FenderId"]),
        preset_dict_value(preset_dict,["audioGraph","nodes", "amp","dspUnitParameters","cabsimType"]),
        preset_dict_value(preset_dict,["audioGraph","nodes", "delay","FenderId"]),
        preset_dict_value(preset_dict,["audioGraph","nodes", "reverb","FenderId"]),
        "hash=TBD" # hashing not yet implemented
    ))

def close_csv_stream():
    print("Closing",_preset_csv_filename)
    _preset_csv_stream.close()
