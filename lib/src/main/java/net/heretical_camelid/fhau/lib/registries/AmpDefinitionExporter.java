package net.heretical_camelid.fhau.lib.registries;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

        // The raw export is the JSON exactly as it was retrieved from the protocol,
        // i.e. compact, with order of dictionary keys preserved.
        String rawJson = pr.m_definitionRawJson;
        String rawTargetPath = m_outputPrefix + "/" + presetBasename + ".raw_preset.json";
        PresetRegistry.outputToFile(rawTargetPath, rawJson);

        // The pretty export is based on the Gson pretty rendering of the parsed JSON object
        // i.e. indented, with dictionary keys sorted, but is post-processed within
        // PresetRecord.prettyJson() to compact the content of the 'connections' node, as
        // this node contains no interesting data and takes up a log of lines in the
        // Gson format.
        String prettyJson = pr.prettyJson();
        String prettyTargetPath = m_outputPrefix + "/" + presetBasename + ".pretty_preset.json";
        PresetRegistry.outputToFile(prettyTargetPath, prettyJson);
    }

    @Override
    public void visitAfterRecords(PresetRegistry registry) {
    }
}
