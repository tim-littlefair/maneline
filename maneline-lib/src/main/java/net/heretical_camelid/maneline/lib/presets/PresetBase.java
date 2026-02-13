package net.heretical_camelid.maneline.lib.presets;


import net.heretical_camelid.maneline.lib.registries.PresetRegistry;

import java.nio.charset.StandardCharsets;

abstract public class PresetBase {

    protected enum CompanionAppName_e {
        FUSE_CLASSIC_USB,
        TONE_DESKTOP_LT_USB,
        TONE_MOBILE_BLE,
    }

    // convenient debug utility, should always be no-op for checked in code
    static private void _trace(Object msg) {
        System.out.println(msg);
    }

    final protected String m_displayName;
    final protected SignalChain m_signalChain;
    final protected CompanionAppName_e m_companionAppName;
    final protected byte[] m_definitionBytes;

    protected PresetBase(
        String displayName,
        SignalChain signalChain,
        CompanionAppName_e companionAppName,
        byte[] definitionBytes
    ) {
        m_displayName = displayName;
        m_signalChain = signalChain;
        m_companionAppName = companionAppName;
        m_definitionBytes = definitionBytes;
    }

    public String displayName() {
        return m_displayName;
    }

    public SignalChain signalChain() {
        return m_signalChain;
    }

    public String modelSpecificJson() {
        // FenderTONE variants for LT, MMP, and other post-2016 models
        // all use JSON-based on-the-wire formats so this definition will
        // do for them.
        // This will need to be overridden for the models which
        // work FenderFUSE (Mustang I-V v1 and v2 and others).
        return new String(m_definitionBytes, StandardCharsets.UTF_8);
    }

    public void export(String exportDirPath, String exportBasename) {
        String rawTargetPath = exportDirPath + "/" + exportBasename + "." + m_companionAppName + ".json";
        PresetRegistry.outputToFile(rawTargetPath, modelSpecificJson().getBytes());
        String canonicalJson = signalChain().toString(4, false);
        String canonicalPath = exportDirPath + "/" + exportBasename + ".json";
        PresetRegistry.outputToFile(canonicalPath, canonicalJson.getBytes());
    }
}
