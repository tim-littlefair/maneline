package net.heretical_camelid.fhau.lib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    }

    private ArrayList<PresetRecord> m_presetRecords;
    private Map<String,PresetRecord> m_nameIndex;
    public PresetInfo() {
        m_presetRecords = new ArrayList<>();
        m_nameIndex = new HashMap<>();
    }
    public void add(PresetRecord presetRecord) {
        if (presetRecord.m_name != null) {
            m_nameIndex.put(presetRecord.m_name, presetRecord);
        }
        m_presetRecords.add(presetRecord);
    }
    public PresetRecord find(String name) {
        return m_nameIndex.get(name);
    }

    public void acceptVisitor(IVisitor prv) {
        for(PresetRecord pr: m_presetRecords ) {
            prv.visit(pr);
        }
    }

    public static void main(String[] args) {
        System.out.println("TODO: tests for PresetInfo");
    }
}
