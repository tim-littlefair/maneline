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



# For the moment this script focusses on the MMP
# When it is expanded to work with LT-series (and GT-series?)
# FMIC devices, the model will need to be discovered rather
# than being hard-coded.
# For MMP, and possibly for GT-series the following line
# in the full JSON dump might be used for this.
# "btcommon.eir_ad.entry.device_name": "Mustang Micro Plus"
# For LT series, the transport is USB rather than BT 
# so the source will be different.

_outdir = None
_model = "mmp"
_presets = {}
_aliases = {}

def open_csv_file(outdir):
    global _outdir
    _outdir = outdir


def preset_dict_value(dict_or_list, key_array,ag_node_id=None):
    if len(key_array)>1:
        if isinstance(dict_or_list,dict):
            return preset_dict_value(dict_or_list[key_array[0]],key_array[1:],ag_node_id)
        else:
            assert isinstance(dict_or_list,list)
            target_node_id = key_array[0]
            return [
                preset_dict_value(d_o_a,key_array[1:],target_node_id)
                for d_o_a in dict_or_list 
                if d_o_a["nodeId"]==target_node_id
            ][0]
    else:
        value = dict_or_list[key_array[0]]
        if value in ("DUBS_Unknown", "DUBS_Passthru"):
            return ""
        elif ag_node_id is not None:
            # The node name is converted to an alias
            # both for shortening, and to reduce naming
            # differences between different model series
            alias = value.replace("DUBS_","")
            alias = alias.replace("ACD_","")
            alias = alias.replace("Fender","")
            alias = alias.replace("GT","")
            alias = alias.replace("BoilerPlate","BlrPlt")
            if alias!=value:
                _aliases[(ag_node_id, alias)] = value
            return alias
        else:
            return value

def add_preset_line(slot_index, preset_dict):
    _presets[slot_index] = preset_dict

def close_csv_stream():

    # The output CSV table of presets of this script should be as 
    # similar as possible to the output of the file PresetCsvGenerator.java 
    # in the directory
    # lib/src/main/java/net/heretical_camelid/fhau/lib/registries.
    # TBD - presently the hash is not included here  - need to decide
    # whether to implement and add it here or remove from the java 
    # output.
    _PRESET_DATA_LINE_FORMAT = "%4d,%-16s,%-18s,%-18s,%-18s,%-18s,%-18s\n"
    _PRESET_HEADER_LINE_FORMAT = _PRESET_DATA_LINE_FORMAT.replace("%4d","%4s")
    preset_csv_stream = open(f"{_outdir}/{_model}_presets.csv","w")
    preset_csv_stream.write(_PRESET_HEADER_LINE_FORMAT%(
        "slot","name", "stomp","mod","amp","delay","reverb" 
    ))
    for slot_index in sorted(_presets.keys()):
        preset_dict = _presets[slot_index]
        preset_csv_stream.write(_PRESET_DATA_LINE_FORMAT%(
            slot_index,
            preset_dict_value(preset_dict,["info","displayName"]),
            preset_dict_value(preset_dict,["audioGraph","nodes", "stomp","FenderId"]),
            preset_dict_value(preset_dict,["audioGraph","nodes", "mod","FenderId"]),
            preset_dict_value(preset_dict,["audioGraph","nodes", "amp","FenderId"]),
            preset_dict_value(preset_dict,["audioGraph","nodes", "delay","FenderId"]),
            preset_dict_value(preset_dict,["audioGraph","nodes", "reverb","FenderId"]),        ))
    preset_csv_stream.close()

    _ALIAS_LINE_FORMAT = "%-6s,%-18s,%s\n"
    alias_csv_stream = open(f"{_outdir}/{_model}_aliases.csv","w")
    alias_csv_stream.write(_ALIAS_LINE_FORMAT%("type","alias","full name"))
    for type in ( "amp", "stomp", "mod","delay","reverb"):
        names = [ key[1] for key in _aliases.keys() if key[0]==type]
        for name in sorted(names):
            alias_csv_stream.write(_ALIAS_LINE_FORMAT%(type,name,_aliases[(type,name)]))
    alias_csv_stream.close()

            



