package net.heretical_camelid.maneline.lib.presets;

import net.heretical_camelid.maneline.lib.registries.PresetJO;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TONE_LT_Preset extends PresetJO {
    public TONE_LT_Preset(byte[] presetBytes) {
        super(new String(presetBytes, StandardCharsets.UTF_8),"tone-usb");
    }
}
