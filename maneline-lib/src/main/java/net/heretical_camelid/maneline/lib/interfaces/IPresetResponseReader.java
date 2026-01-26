package net.heretical_camelid.maneline.lib.interfaces;

import net.heretical_camelid.maneline.lib.presets.SignalChain;
import net.heretical_camelid.maneline.lib.registries.PresetRecord;

public interface IPresetResponseReader {
    void notifyPresetResponse(int slotIndex, PresetRecord presetRecord);
}
