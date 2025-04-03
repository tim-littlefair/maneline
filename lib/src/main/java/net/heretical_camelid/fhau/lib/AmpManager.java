package net.heretical_camelid.fhau.lib;

import net.heretical_camelid.fhau.lib.interfaces.IAmpProvider;
import net.heretical_camelid.fhau.lib.interfaces.ILoggingAgent;

public class AmpManager {
    static ILoggingAgent s_loggingAgent;
    public IAmpProvider m_provider;
    PresetInfo m_presetInfo;
    public AmpManager(ILoggingAgent loggingAgent) {
        s_loggingAgent = loggingAgent;
        m_provider = null;
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
        s_loggingAgent.appendToLog(0,
            String.format("Preset %d requested",whichSlot)
        );
        m_provider.switchPreset(whichSlot);
    }

    private void refresh() {
        m_presetInfo = m_provider.getPresetInfo(null);
    }

    public void setProvider(IAmpProvider provider) {
        s_loggingAgent.appendToLog(5, String.format(
           "AmpManager.setProvider() called old_was_null:%s, new_is_null:%s",
           m_provider==null,provider==null
        ));
        m_provider = provider;
    }

    public void attemptConnection() {
        if(m_provider!=null) {
            IAmpProvider.ProviderState_e connectionOutcomeState = m_provider.attemptConnection();
            s_loggingAgent.appendToLog(5, String.format(
                "AmpManager.attemptConnection() returned %s", connectionOutcomeState
            ));
        } else {
            s_loggingAgent.appendToLog(5,
                "AmpManager.attemptConnection() called while provider was null"
            );
        }
    }
}
