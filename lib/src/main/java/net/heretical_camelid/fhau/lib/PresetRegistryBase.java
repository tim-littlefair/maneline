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

    public void acceptVisitor(Visitor visitor) {
        visitor.visitBeforeRecords(this);
        for (int i: m_records.keySet()) {
            Object record = m_records.get(i);
            if (record != null) {
                visitor.visitRecord(i, record);
            }
        }
        visitor.visitAfterRecords(this);
    }

    public void generateNameTable(PrintStream ostream) {
        this.acceptVisitor(new PresetNameTableGenerator(ostream));
    }

    public void dump() {
        generateNameTable(System.out);
    }

    public static interface Visitor {
        void visitBeforeRecords(PresetRegistryBase registry);
        void visitRecord(int slotIndex, Object record);
        void visitAfterRecords(PresetRegistryBase registry);
    }
}


class PresetRecordBase {
    String m_name;
    public PresetRecordBase(String name) {
        m_name = name;
    }
}

class PresetNameTableGenerator implements PresetRegistryBase.Visitor {
    PrintStream m_printStream;
    PresetNameTableGenerator(PrintStream printStream) {
        m_printStream = printStream;
    }
    @Override
    public void visitBeforeRecords(PresetRegistryBase registry) {
        m_printStream.println("Presets");
        m_printStream.println(String.format("%3s %16s", "---", "----------------"));
        m_printStream.println(String.format("%3s %16s", " # ", "      Name      "));
        m_printStream.println(String.format("%3s %16s", "---", "----------------"));
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        PresetRecordBase prb = (PresetRecordBase) record;
        assert prb != null;
        m_printStream.println(String.format("%3d %16s", slotIndex, prb.m_name));
    }

    @Override
    public void visitAfterRecords(PresetRegistryBase registry) {
    }
}