package net.heretical_camelid.fhau.lib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applications based on the FHAU framework can only activate presets
 * which are available in the attached amplifier.
 * Applications need to send IAmplifierProvider.getPresets request
 * to the connected amplifier provider object and will receive a
 * PresetInfo object in return.
 * IAmplifierProvider.getPresets accepts a nullable instance of
 * the PresetInfo class - if provided the provider can use this to filter
 * which presets should be reported back.
 */
public class PresetInfo {
    public enum PresetState {
        /**
         * The application interested in the preset has requested it
         * from the provider.
         */
        REQUESTED,
        /**
         * The provider is providing details of a preset supported by the
         * amplifier.
         */
        RETURNED,
        /**
         * The application has tentatively accepted the offered preset.
         * This might apply if the provider returns a preset with the right
         * name but different hash from the one desired by the application.
         * The application's UX may require a confirmation from the user
         * before allowing a tentatively accepted preset to be used.
         */
        TENTATIVE,
        /**
         * The application has accepted the offered preset unconditionally.
         */
        ACCEPTED,
        /**
         * The application has rejected the offered preset unconditionally.
         */
        REJECTED,
        /**
         * The provider can indicate that a requested preset is not available.
         */
        NOT_AVAILABLE
    };

    static public class PresetRecord {
        public PresetState m_state = null;
        public String m_name=null;
        public int m_slotNumber=0;
        public String m_hash=null;

        public PresetRecord(String name, int slotNumber) {
            m_state = PresetState.REQUESTED;
            m_name = name;
            m_slotNumber = slotNumber;
        }
    }

    public interface IVisitor {
        void visit(PresetRecord pr);
    };

    private List<PresetRecord> m_presetRecords;
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
}
