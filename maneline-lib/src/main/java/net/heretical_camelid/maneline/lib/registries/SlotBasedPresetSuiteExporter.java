package net.heretical_camelid.maneline.lib.registries;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


public class SlotBasedPresetSuiteExporter implements PresetRegistry.Visitor {

    static String s_sourceDeviceDetails = null;

    public static void setSourceDeviceDetails(String sdd) {
        s_sourceDeviceDetails = sdd;
    }

    final String m_outputPrefix;

    final String m_suiteName;

    final List<Integer> m_desiredSlotIndexes;

    JSONObject m_suite;

    HashMap<Integer,PresetRecord> m_presetRecords;

    SlotBasedPresetSuiteExporter(
        String outputPrefix, String suiteName, Integer... desiredSlotIndexes
    ) {
        m_outputPrefix = outputPrefix;
        m_suiteName = suiteName;
        m_desiredSlotIndexes = Arrays.asList(desiredSlotIndexes);
        m_suite = new JSONObject();
        m_suite.put("suiteName", suiteName);
        m_suite.put("presets", new JSONArray());
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
            JSONObject presetObject = new JSONObject();
            // TODO: get rid of originSlotIndex when Lua has a
            // lookup based on hash and name
            // Until then, this only works if the slot index on
            // the target is the same as the slot index when the
            // suite was created
            presetObject.put("originSlotIndex", slotIndex);
            presetObject.put("presetName", fjpr.m_name);
            presetObject.put("audioHash", fjpr.audioHash());
            presetObject.put("effectsSummary", fjpr.effects(
                PresetRecord.EffectsLevelOfDetails.MODULES_ONLY
            ));
            presetObject.put("effectsDetails", fjpr.effects(
                PresetRecord.EffectsLevelOfDetails.MODULES_AND_PARAMETERS
            ));
            presetObject.put("shortInfo", fjpr.shortInfo());
            if (s_sourceDeviceDetails != null) {
                presetObject.put(
                    "originDevice",s_sourceDeviceDetails+" slot "+ slotIndex
                );
            }
            m_suite.getJSONArray("presets").put(presetObject);
            m_presetRecords.put(slotIndex,fjpr);
        }

    }

    @Override
    public void visitAfterRecords(PresetRegistry registry) {
        String jsonForSuite = m_suite.toString(4);
        String suiteFilename = m_suiteName.replace(" ", "_");
        String targetPath = m_outputPrefix + "/" + suiteFilename + ".preset_suite.json";
        PresetRegistry.outputToFile(targetPath, jsonForSuite.getBytes());
    }
}
