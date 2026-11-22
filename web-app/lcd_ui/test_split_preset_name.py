from manage_lcd_ui import split_preset_name

def name_for_amp(amp_details, psname):
    psdetails = { "psname": psname }
    split_preset_name(amp_details, psdetails)
    return psdetails

if __name__ == "__main__":
    dd_classic = { "ampname": "Mustang I/II v2" }
    dd_lt = { "ampname": "Mustang LT40S" }

    print(name_for_amp(dd_classic, "Chimey Deluxe"))
    print(name_for_amp(dd_classic, "ABCDEFGHIJKLMNOPQRST"))
    print(name_for_amp(dd_classic, "Brutal Metal II"))
    print(name_for_amp(dd_classic, "Pigs Can Fly"))
    print(name_for_amp(dd_classic, "What The #^[!"))
