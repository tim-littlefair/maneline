package net.heretical_camelid.fhau.lib;

import java.io.PrintStream;
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
public class PresetRegistryBase {
    HashMap<Integer, PresetRecordBase> m_records;

    public PresetRegistryBase() {
        m_records = new HashMap<>();
    }

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

    public void generateNameTable(PrintStream ostream) {
        this.acceptVisitor(new PresetNameTableGenerator(ostream));
    }
}

interface PresetRegistryVisitor {
    void visit(PresetRegistryBase registry);
    void visit(int slotIndex, Object record);
}

class PresetRecordBase {
    String m_name;
    public PresetRecordBase(String name) {
        m_name = name;
    }
}

class PresetNameTableGenerator implements PresetRegistryVisitor {
    PrintStream m_printStream;
    PresetNameTableGenerator(PrintStream printStream) {
        m_printStream = printStream;
    }
    @Override
    public void visit(PresetRegistryBase registry) {
        m_printStream.println("Presets");
        m_printStream.println(String.format("%3s %16s", "---", "----------------"));
        m_printStream.println(String.format("%3s %16s", " # ", "      Name      "));
        m_printStream.println(String.format("%3s %16s", "---", "----------------"));
    }

    @Override
    public void visit(int slotIndex, Object record) {
        PresetRecordBase prb = (PresetRecordBase) record;
        assert prb != null;
        m_printStream.println(String.format("%3d %16s", slotIndex, prb.m_name));
    }
}