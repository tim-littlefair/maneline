package net.heretical_camelid.maneline.lib.registries;

import java.io.PrintStream;

class PresetNameTableGenerator implements PresetRegistry.Visitor {
    PrintStream m_printStream;

    PresetNameTableGenerator(PrintStream printStream) {
        m_printStream = printStream;
    }

    @Override
    public void visitBeforeRecords(PresetRegistry registry) {
        m_printStream.println("Presets");
        m_printStream.println(String.format("%3s %16s", "---", "----------------"));
        m_printStream.println(String.format("%3s %16s", " # ", "      Name      "));
        m_printStream.println(String.format("%3s %16s", "---", "----------------"));
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        PresetRecord prb = (PresetRecord) record;
        assert prb != null;
        m_printStream.println(String.format("%3d %16s", slotIndex, prb.m_name));
    }

    @Override
    public void visitAfterRecords(PresetRegistry registry) {
    }
}
