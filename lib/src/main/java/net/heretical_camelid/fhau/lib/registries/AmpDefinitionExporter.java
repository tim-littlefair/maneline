package net.heretical_camelid.fhau.lib.registries;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

class AmpDefinitionExporter implements PresetRegistryBase.Visitor {
    final String m_outputPrefix;
    final Gson m_compactGson;
    final Gson m_prettyGson;

    AmpDefinitionExporter(String outputPrefix) {
        m_outputPrefix = outputPrefix;
        m_compactGson = new Gson();
        m_prettyGson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public void visitBeforeRecords(PresetRegistryBase registry) {
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        FenderJsonPresetRegistry.Record fjpr = (FenderJsonPresetRegistry.Record) record;
        assert fjpr != null;
        String presetBasename = String.format(
            "%s-%s",
            fjpr.displayName().replace(" ", "_"),
            fjpr.audioHash()
        );

        // The raw export is the JSON exactly as it was retrieved from the protocol,
        // i.e. compact, with order of dictionary keys preserved.
        String rawJson = fjpr.m_definitionRawJson;
        String rawTargetPath = m_outputPrefix + "/" + presetBasename + ".raw_preset.json";
        FenderJsonPresetRegistry.outputToFile(rawTargetPath, rawJson);

        // We also export the GSON compact rendering so that we can compare
        // it to the raw JSON received from the amp.
        String compactJson = m_compactGson.toJson(fjpr.m_presetCanonicalSerializer);
        String compactTargetPath = m_outputPrefix + "/" + presetBasename + ".compact_preset.json";
        FenderJsonPresetRegistry.outputToFile(compactTargetPath, compactJson);

        // The pretty export is the GSON pretty rendering of the parsed JSON object
        // i.e. indented, with dictionary keys sorted.
        String prettyJson = m_prettyGson.toJson(fjpr.m_presetCanonicalSerializer);
        String prettyTargetPath = m_outputPrefix + "/" + presetBasename + ".pretty_preset.json";
        FenderJsonPresetRegistry.outputToFile(prettyTargetPath, prettyJson);
    }

    @Override
    public void visitAfterRecords(PresetRegistryBase registry) {
    }
}
