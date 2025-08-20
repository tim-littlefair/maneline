package net.heretical_camelid.maneline.lib;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Applications based on the FHAU framework can only activate presets
 * which are available in the attached amplifier.
 * Applications need to send IAmplifierProvider.getPresets request
 * to the connected amplifier provider object and will receive a
 * PresetInfo object in return.
 * IAmplifierProvider.getPresets accepts a nullable instance of
 * the PresetInfo class - if this parameter is non-null the provider
 * can use this to filter which presets should be reported back.
 */
public class PresetInfo {
    public interface IVisitor {
        void visit(PresetRecord pr);
        void setActivePresetIndex(int activePresetIndex);
    }
    public void acceptVisitor(IVisitor visitor) {
        for(int presetIndex: m_presetRecords.keySet()) {
            visitor.visit(m_presetRecords.get(presetIndex));
        }
        visitor.setActivePresetIndex(m_activePresetIndex);
    }

    public Map<Integer, PresetRecord> m_presetRecords;
    private Map<String,Integer> m_nameIndex;
    private int m_activePresetIndex;
    public PresetInfo() {
        m_presetRecords = new TreeMap<>(); // TreeMap.keySet() is sorted
        m_nameIndex = new HashMap<>();
        m_activePresetIndex = 0;
    }
    public void add(PresetRecord presetRecord) {
        assert presetRecord.m_slotNumber > 0;
        if (presetRecord.m_name != null) {
            m_presetRecords.put(
                presetRecord.m_slotNumber,
                presetRecord
            );
            m_nameIndex.put(presetRecord.m_name, presetRecord.m_slotNumber);
            if(m_activePresetIndex==0) {
                m_activePresetIndex = presetRecord.m_slotNumber;
            }
        }
    }
    public PresetRecord find(String name) {
        return m_presetRecords.get(m_nameIndex.get(name));
    }

    public static void main(String[] args) {
        System.out.println("TODO: tests for PresetInfo");
    }
}
