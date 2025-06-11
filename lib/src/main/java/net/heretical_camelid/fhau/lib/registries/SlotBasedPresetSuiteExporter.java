package net.heretical_camelid.fhau.lib.registries;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

public class SlotBasedPresetSuiteExporter implements PresetRegistryBase.Visitor {
    static final Gson m_gson = new GsonBuilder().setPrettyPrinting().create();

    static String s_sourceDeviceDetails = null;

    final String m_outputPrefix;

    final String m_suiteName;

    final List<Integer> m_desiredSlotIndexes;

    JsonObject m_suite;

    public static void setSourceDeviceDetails(String sdd) {
        s_sourceDeviceDetails = sdd;
    }

    SlotBasedPresetSuiteExporter(
        String outputPrefix, String suiteName, Integer... desiredSlotIndexes
    ) {
        m_outputPrefix = outputPrefix;
        m_suiteName = suiteName;
        m_desiredSlotIndexes = Arrays.asList(desiredSlotIndexes);
        m_suite = new JsonObject();
        m_suite.addProperty("suiteName", suiteName);
        m_suite.add("presets", new JsonArray());
    }

    @Override
    public void visitBeforeRecords(PresetRegistryBase registry) {
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        FenderJsonPresetRegistry.Record fjpr = (FenderJsonPresetRegistry.Record) record;
        assert fjpr != null;
        if (m_desiredSlotIndexes.contains(slotIndex)) {
            JsonObject presetObject = new JsonObject();
            presetObject.addProperty("presetName", fjpr.m_name);
            presetObject.addProperty("audioHash", fjpr.audioHash());
            presetObject.addProperty("effects", fjpr.effects());
            presetObject.addProperty("shortInfo", fjpr.shortInfo());
            if (s_sourceDeviceDetails != null) {
                presetObject.addProperty(
                    "sourceDevice",
                    String.format("%s slot %d",
                        s_sourceDeviceDetails, slotIndex
                    )
                );
            }
            m_suite.getAsJsonArray("presets").add(presetObject);
        }
    }

    @Override
    public void visitAfterRecords(PresetRegistryBase registry) {
        String jsonForSuite = m_gson.toJson(m_suite);
        String suiteFilename = m_suiteName.replace(" ", "_");
        String targetPath = m_outputPrefix + "/" + suiteFilename + ".preset_suite.json";
        FenderJsonPresetRegistry.outputToFile(targetPath, jsonForSuite);
    }
}
