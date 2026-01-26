package net.heretical_camelid.maneline.lib.registries;

class AmpDefinitionExporter implements PresetRegistry.Visitor {
    final String m_outputPrefix;

    AmpDefinitionExporter(String outputPrefix) {
        m_outputPrefix = outputPrefix;
    }

    @Override
    public void visitBeforeRecords(PresetRegistry registry) {
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        PresetRecord pr = (PresetRecord) record;
        assert pr != null;
        String presetBasename = pr.exportBasename();
        pr.m_preset.export(m_outputPrefix,presetBasename);
    }

    @Override
    public void visitAfterRecords(PresetRegistry registry) {
    }
}
