package net.heretical_camelid.maneline.lib.presets;


import net.heretical_camelid.maneline.lib.registries.PresetRegistry;

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

    public void export(String exportDirPath, String exportBasename) {
        String rawTargetPath = exportDirPath + "/" + exportBasename + "." + m_companionAppName + ".raw";
        PresetRegistry.outputToFile(rawTargetPath, m_definitionBytes);
        String canonicalJson = signalChain().toString(4, false);
        String canonicalPath = exportDirPath + "/" + exportBasename + ".json";
        PresetRegistry.outputToFile(canonicalPath, canonicalJson.getBytes());
    }

    static public void main(String[] args) {
/*
        PresetBase testPreset1 = (PresetBase) (new PresetBase().put("displayName","testPreset1"));
        System.out.println(testPreset1.toString(4));
        JSONArray nodesArray1 = testPreset1.m_audioGraph_nodes;
        assert nodesArray1 != null;
        assert nodesArray1.length()==5;
        for(int i=0; i<nodesArray1.length(); ++i) {
         assert nodesArray1.isNull(i);
        }

        PresetBase testPreset2 = (PresetBase) (new PresetBase().put("displayName","testPreset2"));
        testPreset2.addAudioGraphNode(
         "Deluxe65", "amp",
         "{}",
         2
        );
        System.out.println(testPreset2.toString(4));
        assert testPreset2.m_audioGraph_nodes.length() == 5;
        String ampFenderId = (String) getSubObject(
         testPreset2,
         List.of("audioGraph","nodes", 2, "FenderId")
        );
        assert ampFenderId.equals("Deluxe65");

        PresetBase testPreset3 = create(testPreset2.exportPresetRecord());
        testPreset3.addAudioGraphNode("Passthru","stomp",null,0);
        testPreset3.addAudioGraphNode("SineTremolo","mod",null,1);
        testPreset3.addAudioGraphNode("Passthru","delay",null,3);
        testPreset3.addAudioGraphNode("Passthru","reverb",null,4);
        System.out.println(testPreset3.toString(4));
        // The hash below needs to be maintained manually
        PresetRecord pr3 = testPreset3.exportPresetRecord();
        String expectedHash = "7b19-14e2";
        assert pr3.audioHash().equals(expectedHash): String.format(
         "audioHash mismatch: expected=%s actual=%s",
         expectedHash, pr3.audioHash()
        );
*/
        System.exit(0);
    }
}
