package net.heretical_camelid.fhau.lib;

import java.util.HashMap;

/**
 * PresetRegistryBase is a minimal registry of presets which maintains
 * a collection of simple preset objects which consist of a slot number
 * and name only.
 * Both the registry class and the simple preset objects can be extended
 * to support more complex behaviour.
 */
public class PresetRegistryBase {
    HashMap<Integer, PresetRecordBase> m_records;

    public PresetRegistryBase() {
        m_records = new HashMap<>();
    }

    public void register(int slotIndex, PresetRecordBase presetRecord) {
        assert slotIndex > 0;
        m_records.put(slotIndex, presetRecord);
    }

    public void acceptVisitor(PresetRegistryVisitor visitor) {
        visitor.visit(this);
        for (int i = 1; i < m_records.size(); ++i) {
            PresetRecordBase record = m_records.get(i);
            if (record != null) {
                visitor.visit(i, record);
            }
        }
    }
}
