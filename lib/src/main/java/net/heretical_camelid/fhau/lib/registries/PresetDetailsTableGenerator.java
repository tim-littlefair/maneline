package net.heretical_camelid.fhau.lib.registries;

import java.io.PrintStream;
import java.util.ArrayList;

public class PresetDetailsTableGenerator implements PresetRegistryBase.Visitor {
    private final static String _LINE_FORMAT = "%3d %-16s %-9s %-70s";
    PrintStream m_printStream;

    public PresetDetailsTableGenerator(PrintStream printStream) {
        m_printStream = printStream;
    }

    @Override
    public void visitBeforeRecords(PresetRegistryBase registry) {
        m_printStream.println();
        m_printStream.println("Unique Presets");
        m_printStream.println(String.format(
            _LINE_FORMAT.replace("%3d", "%3s"),
            "#", "Name", "Hash", "Effect Chain"
        ));
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        FenderJsonPresetRegistry.Record fjpr = (FenderJsonPresetRegistry.Record) record;
        assert fjpr != null;
        m_printStream.println(String.format(
            _LINE_FORMAT,
            slotIndex, fjpr.displayName(), fjpr.audioHash(), fjpr.effects()
        ));
    }

    @Override
    public void visitAfterRecords(PresetRegistryBase registry) {
        FenderJsonPresetRegistry fjpRegistry = (FenderJsonPresetRegistry) registry;
        assert fjpRegistry != null;
        m_printStream.println();
        m_printStream.println("Duplicated Presets");
        for (String duplicateKey : fjpRegistry.m_duplicateSlots.keySet()) {
            ArrayList<Integer> duplicateSlotList = fjpRegistry.m_duplicateSlots.get(duplicateKey);
            if (duplicateSlotList.size() == 1) {
                continue;
            }
            m_printStream.println(String.format(
                "The preset with %s is duplicated at the following slots: %s",
                duplicateKey, duplicateSlotList
            ));
        }
    }
}
