package net.heretical_camelid.maneline.lib.registries;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class SlotBasedPresetSuiteExporter implements PresetRegistry.Visitor {

    static final Gson m_gson = new GsonBuilder().setPrettyPrinting().create();

    static String s_sourceDeviceDetails = null;

    public static void setSourceDeviceDetails(String sdd) {
        s_sourceDeviceDetails = sdd;
    }

    final String m_outputPrefix;

    final String m_suiteName;

    final List<Integer> m_desiredSlotIndexes;

    JsonObject m_suite;

    HashMap<Integer,PresetRecord> m_presetRecords;

    SlotBasedPresetSuiteExporter(
        String outputPrefix, String suiteName, Integer... desiredSlotIndexes
    ) {
        m_outputPrefix = outputPrefix;
        m_suiteName = suiteName;
        m_desiredSlotIndexes = Arrays.asList(desiredSlotIndexes);
        m_suite = new JsonObject();
        m_suite.addProperty("suiteName", suiteName);
        m_suite.add("presets", new JsonArray());
        m_presetRecords = new HashMap<>();
    }

    @Override
    public void visitBeforeRecords(PresetRegistry registry) {
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        PresetRecord fjpr = (PresetRecord) record;
        assert fjpr != null;
        if (
            m_desiredSlotIndexes.contains(slotIndex) || 
            m_desiredSlotIndexes.size()==0
        ) {
            JsonObject presetObject = new JsonObject();
            presetObject.addProperty("originSlotIndex", slotIndex);
            presetObject.addProperty("presetName", fjpr.m_name);
            presetObject.addProperty("audioHash", fjpr.audioHash());
            presetObject.addProperty("effectsSummary", fjpr.effects(
                PresetRecord.EffectsLevelOfDetails.MODULES_ONLY
            ));
            presetObject.addProperty("effectsDetails", fjpr.effects(
                PresetRecord.EffectsLevelOfDetails.MODULES_AND_PARAMETERS
            ));
            presetObject.addProperty("shortInfo", fjpr.shortInfo());
            if (s_sourceDeviceDetails != null) {
                presetObject.addProperty(
                    "originDevice",s_sourceDeviceDetails
                );
            }
            m_suite.getAsJsonArray("presets").add(presetObject);
            m_presetRecords.put(slotIndex,fjpr);
        }

    }

    @Override
    public void visitAfterRecords(PresetRegistry registry) {
        String jsonForSuite = m_gson.toJson(m_suite);
        String suiteFilename = m_suiteName.replace(" ", "_");
        String targetPath = m_outputPrefix + "/" + suiteFilename + ".preset_suite.json";
        PresetRegistry.outputToFile(targetPath, jsonForSuite);
    }
}
