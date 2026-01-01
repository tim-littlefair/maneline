package net.heretical_camelid.maneline.lib.registries;

class AmpDefinitionExporter implements PresetRegistry.Visitor {
    final String m_outputPrefix;
    // final Gson m_prettyGson;

    AmpDefinitionExporter(String outputPrefix) {
        m_outputPrefix = outputPrefix;
        // m_prettyGson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public void visitBeforeRecords(PresetRegistry registry) {
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        PresetRecord pr = (PresetRecord) record;
        assert pr != null;
        String presetBasename = String.format(
            "%s-%s",
            pr.displayName().replace(" ", "_"),
            pr.audioHash()
        );

        // The raw export is the preset exactly as it was retrieved from the protocol,
        // i.e.
        // + for classic: hex of binary payloads
        // + for LT: compact JSON, with order of dictionary keys preserved.
        String presetRawDefinition = pr.m_rawDefinition;
        String rawTargetPath = m_outputPrefix + "/" + presetBasename + ".raw";
        PresetRegistry.outputToFile(rawTargetPath, presetRawDefinition);

        // The pretty export is based on the Gson pretty rendering of the parsed JSON object
        // i.e. indented, with dictionary keys sorted, but is post-processed within
        // PresetRecord.prettyJson() to compact the content of the 'connections' node, as
        // this node contains no interesting data and takes up a log of lines in the
        // Gson format.
        String prettyJson = pr.prettyJson();
        String prettyTargetPath = m_outputPrefix + "/" + presetBasename + ".json";
        PresetRegistry.outputToFile(prettyTargetPath, prettyJson);
    }

    @Override
    public void visitAfterRecords(PresetRegistry registry) {
    }
}
