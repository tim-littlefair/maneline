package net.heretical_camelid.fhau.lib;

public class AmpManager {
    final IAmpProvider m_provider;

    PresetInfo m_presetInfo;
    public AmpManager(IAmpProvider provider) {
        m_provider = provider;
        m_provider.connect();
    }

    public PresetInfo getPresets() {
        refresh();
        PresetInfo retval = m_presetInfo;
        // TODO:
        // We might want to add a PresetInfo parameter and
        // filter the return to only include the intersection
        // of requested and available presets.
        return retval;
    }

    public void switchPreset(int whichSlot) {
        /*
        String commandHexString = String.format(
            "35:07:08:00:8a:02:02:08:%02x",
            whichSlot
        );
        appendToLog(String.format("Requesting switch to preset %02d",whichSlot));
        m_ampManager.sendCommand(commandHexString, m_sbLog);
        appendToLog("Preset switch command sent");
         */
    }

    private void refresh() {
        m_presetInfo = m_provider.getPresetInfo(null);
    }
}
