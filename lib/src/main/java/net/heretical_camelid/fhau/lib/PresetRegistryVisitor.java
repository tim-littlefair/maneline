package net.heretical_camelid.fhau.lib;

interface PresetRegistryVisitor {
    void visit(PresetRegistryBase registry);

    void visit(int slotIndex, PresetRecordBase record);
}
