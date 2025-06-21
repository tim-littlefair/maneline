package net.heretical_camelid.fhau.lib.registries;

import java.util.ArrayList;
import java.util.HashMap;

public class SuiteRegistry {
    final PresetRegistry m_presetRegistry;
    ArrayList<PresetSuiteEntry> m_suites;
    final HashMap<String, HashMap<Integer, PresetRecord>> m_suitePresets;

    public SuiteRegistry(PresetRegistry registry) {
        m_presetRegistry = registry;
        m_suitePresets = new HashMap<>();
        m_suites = new ArrayList<>();
    }

    // The user may want to select a range of presets for export
    // (for example on LT40S to capture only firmware default presets 1-30)
    int m_minSlotIndex = 1;
    int m_maxSlotIndex = 999;
    void setRange(int minSlotIndex, int maxSlotIndex) {
        m_minSlotIndex = minSlotIndex;
        m_maxSlotIndex = maxSlotIndex;
    }

    public String nameAt(int position) {
        return m_suites.get(position).m_suiteName;
    }

    public HashMap<Integer, PresetRecord> recordsAt(int position) {
        return m_suites.get(position).m_presetRecords;
    }

    public PresetSuiteEntry createPresetSuiteEntry(
        String suiteName,
        ArrayList<HashMap<String,String>> presets
    ) {
        HashMap<Integer, PresetRecord> presetRecords = new HashMap<>();
        for(HashMap<String,String> presetAttributes: presets) {
            String presetName = presetAttributes.get("presetName");
            String audioHash = presetAttributes.get("audioHash");
            Integer slotIndex = m_presetRegistry.findAudioHash(audioHash, presetName);
            if(slotIndex!=null) {
                PresetRecord presetRecord= m_presetRegistry.get(slotIndex);
                assert presetRecord!=null;
                System.out.println(String.format(
                     "Preset '%s' found for audioHash='%s'",
                     presetName, audioHash
                ));
                presetRecords.put(slotIndex,presetRecord);
            } else {
                System.out.println(String.format(
                    "No preset record found for preset '%s' audioHash='%s'",
                    presetName, audioHash
                ));
            }
        }
        if(presetRecords.isEmpty()) {
            System.out.println("No preset records found for suite " + suiteName);
            return null;
        } else {
            PresetSuiteEntry newPSE = new PresetSuiteEntry(suiteName,presetRecords);
            m_suites.add(newPSE);
            m_suitePresets.put(suiteName,newPSE.m_presetRecords);
            return newPSE;
        }
    }

    public static class PresetSuiteEntry {
        String m_suiteName;
        HashMap<Integer, PresetRecord> m_presetRecords;
        PresetSuiteEntry(
            String suiteName,
            HashMap<Integer, PresetRecord> presetRecords
        ) {
            m_suiteName = suiteName;
            m_presetRecords = presetRecords;
        }

        public String name() {
            return m_suiteName;
        }
    }

    public static String buttonLabel(int slotIndex, String displayName) {
        assert displayName.length()==16;
        final String ZWNBS = "\uFEFF"; // Unicode zero width no-break space
        String line1=displayName.substring(0,8).strip();
        if(line1.length()==0) {
            line1 = ZWNBS;
        }
        String line2=displayName.substring(8,16).strip();
        if(line2.length()==0) {
            line2 = ZWNBS;
        }
        return String.format("%03d\n%s\n%s",slotIndex,line1,line2);
    }

    public int firstSuiteForSlotIndex(int slotIndex) {
        for(int i = 0; i< m_suites.size(); ++i) {
            for(int j: m_suites.get(i).m_presetRecords.keySet()) {
                if(j==slotIndex) {
                    return i;
                }
            }
        }
        return -1;
    }

    public void dump() {
        System.out.println("Preset Suites");
        for(PresetSuiteEntry pse: m_suites) {
            System.out.println(pse.m_suiteName);
            ArrayList<Integer> presetSlots = new ArrayList<>(pse.m_presetRecords.keySet());
            presetSlots.sort(null);
            for(int i: presetSlots) {
                System.out.println(String.format(
                    "    %03d %s", i, pse.m_presetRecords.get(i).displayName()
                ));
            }
        }
    }
}
