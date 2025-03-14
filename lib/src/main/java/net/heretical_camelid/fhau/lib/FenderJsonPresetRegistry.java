package net.heretical_camelid.fhau.lib;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * PresetRegistryBase is a minimal registry of presets which maintains
 * a collection of simple preset objects which consist of a slot number
 * and name only.
 * Both the registry class and the simple preset objects can be extended
 * to support more complex behaviour.
 * Note that the base registry class is responsible for creating the
 * record objects it stores.  Classes which extend the base registry
 * class to are expected to create record objects which contain additional
 * data beyond the slot index and name, so the name, of a more Extended implementations of the registry are expected
 *
 */
public class FenderJsonPresetRegistry extends PresetRegistryBase {
    public FenderJsonPresetRegistry() {  }

    public void register(int slotIndex, String name, byte[] definition) {
        // Slots are numbered from 1
        assert slotIndex > 0;
        // This registry can only store slotIndex and name, so definition must be null
        assert definition == null;

        m_records.put(slotIndex, new PresetRecordBase(name));
    }

    public void acceptVisitor(PresetRegistryVisitor visitor) {
        visitor.visit(this);
        for (int i = 1; i < m_records.size(); ++i) {
            Object record = m_records.get(i);
            if (record != null) {
                visitor.visit(i, record);
            }
        }
    }
}

class FenderJsonPresetRecord extends PresetRecordBase {
    final String m_definitionRawJson;
    public FenderJsonPresetRecord(String name, byte[] definitionBytes) {
        super(name);
        m_definitionRawJson = new String(definitionBytes, StandardCharsets.UTF_8);
    }
}

