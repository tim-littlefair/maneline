package net.heretical_camelid.maneline.lib.registries;

class AmpDefinitionExporter implements PresetRegistry.Visitor {
    final String m_outputPrefix;

    AmpDefinitionExporter(String outputPrefix) {
        m_outputPrefix = outputPrefix;
    }

    @Override
    public void visitBeforeRecords(PresetRegistry registry) {
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        PresetRecord pr = (PresetRecord) record;
        assert pr != null;
        String presetBasename = pr.exportBasename();

        // The raw export is the preset exactly as it was retrieved from the protocol,
        // i.e.
        // + for classic: readable JSON including hex of the 8 64 byte packets
        //   retrieved by the getPreset(...) command;
        // + for LT: compact JSON, with order of dictionary keys preserved.
        String presetRawDefinition = pr.m_rawDefinition;
        String rawTargetPath = m_outputPrefix + "/" + presetBasename + pr.exportRawExtension();
        PresetRegistry.outputToFile(rawTargetPath, presetRawDefinition);

        // The pretty export is based on the org.json.JSONObject.toString(4) rendering
        // of the minimal parsed JSON object
        // i.e. indented, with dictionary keys sorted.
        // Note that the 'audioGraph.connections' array in the FenderTONE LT desktop
        // and mobile is not supplied, but the signal chain ordering information
        // which that array verbosely represents has been retained by sorting the
        // audioGraph.nodes array to reflect the signal chain order.
        String prettyJson = pr.prettyJson();
        String prettyTargetPath = m_outputPrefix + "/" + presetBasename + ".json";
        PresetRegistry.outputToFile(prettyTargetPath, prettyJson);
    }

    @Override
    public void visitAfterRecords(PresetRegistry registry) {
    }
}
