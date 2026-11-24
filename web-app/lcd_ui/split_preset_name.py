#!/bin/python3

import math

def _find_good_split(psname):
    midchar_index = math.floor((len(psname)+1)/2)
    middle_range_start = math.floor((len(psname)+2)/3)
    middle_range_end = len(psname) - middle_range_start
    distance_from_midchar = 0
    while True:
        # The logic below is intended to find a split position in a 
        # camel case name, breaking where a capital letter follows any 
        # character which is not a capital
        candidate_split_position = midchar_index + distance_from_midchar
        if (
            psname[candidate_split_position].isupper() and
            not psname[candidate_split_position-1].isupper()
        ):
            return candidate_split_position
        candidate_split_position = midchar_index - distance_from_midchar
        if (
            psname[candidate_split_position+1].isupper() and
            not psname[candidate_split_position].isupper()
        ):
            return candidate_split_position
        if (2*distance_from_midchar) > (middle_range_end-middle_range_start):
            # All of middle third has been tested without finding a good
            # place to split, so we give up and just return the middle 
            # character (biassed to the right for even length names)
            return midchar_index
        distance_from_midchar += 1

def split_preset_name(device_details, preset_details):
    psname = preset_details["psname_full"]
    if "LT" in device_details["ampname"]:
        # LT presets all have names exactly 16 characters long
        # and both Fender Tone Desktop and the amp itself split
        # this into two lines 8+8
        preset_details["psname1"] = psname[0:8].strip()
        preset_details["psname2"] = psname[8:16].strip()
    else:
        # For other amps we make best effort to find a good place 
        # to split
        psname = psname.strip()
        psnamelen = len(psname)
        space_positions = [ p for p in range(0,len(psname)) if psname[p]==" "]
        if len(space_positions)==0:
            split_position = _find_good_split(psname)
            preset_details["psname1"] = psname[:split_position].strip()
            preset_details["psname2"] = psname[split_position:].strip()
        else:
            while(len(space_positions)>1):
                if psnamelen-space_positions[-1]<=space_positions[0]:
                    space_positions.remove(space_positions[-1])
                else:
                    space_positions.remove(space_positions[0])
            removable_space_position = space_positions[0]
            preset_details["psname1"] = psname[:removable_space_position].strip()
            preset_details["psname2"] = psname[removable_space_position+1:].strip()


if __name__ == "__main__":
    # Lazy test - should break this out and use a proper unit test framework
    
    def name_for_amp(amp_details, psname):
        psdetails = { "psname_full": psname }
        split_preset_name(amp_details, psdetails)
        return psdetails

    dd_classic = { "ampname": "Mustang I/II v2" }
    dd_lt = { "ampname": "Mustang LT40S" }

    print(name_for_amp(dd_classic, "scanning presets"))
    print(name_for_amp(dd_classic, "Chimey Deluxe"))
    print(name_for_amp(dd_classic, "ABCDEFGHIJKLMNOPQRST"))
    print(name_for_amp(dd_classic, "Brutal Metal II"))
    print(name_for_amp(dd_classic, "Pigs Can Fly"))
    print(name_for_amp(dd_classic, "What The #^[!"))
    print(name_for_amp(dd_classic, "American90sChorus"))
