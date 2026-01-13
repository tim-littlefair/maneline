package net.heretical_camelid.maneline.lib.presets;

import java.nio.charset.StandardCharsets;

public class TONE_LT_Preset extends PresetBase {
    public TONE_LT_Preset(byte[] presetBytes) {
        super(new String(presetBytes, StandardCharsets.UTF_8),"tone-usb");
    }
}
