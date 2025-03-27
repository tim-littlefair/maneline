package net.heretical_camelid.fhau.lib;

import java.util.ArrayList;
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

    // All functions beyond this point are package-private and
    // exist to provide convenient test/simulation data for other classes
    // in this package.

    static PresetInfo piMixedBag() {
        PresetInfo piMixedBag = new PresetInfo();

        PresetRecord pr1 = new PresetRecord("COOL SOUND",1);
        pr1.m_state = PresetRecord.PresetState.ACCEPTED;
        piMixedBag.add(pr1);

        PresetRecord pr2 = new PresetRecord("WARM SOUND",2);
        pr2.m_state = PresetRecord.PresetState.ACCEPTED;
        piMixedBag.add(pr2);

        // We want to support sparse instances of PresetInfo, so
        // this one is an example
        PresetRecord pr5 = new PresetRecord("SPARSE SOUND",5);
        pr5.m_state = PresetRecord.PresetState.ACCEPTED;
        piMixedBag.add(pr5);

        PresetRecord pr10 = new PresetRecord("NEW SOUND",10);
        pr10.m_state = PresetRecord.PresetState.TENTATIVE;
        piMixedBag.add(pr10);

        PresetRecord pr11 = new PresetRecord("NASTY SOUND",11);
        pr11.m_state = PresetRecord.PresetState.REJECTED;
        piMixedBag.add(pr11);

        return piMixedBag;
    }
}
