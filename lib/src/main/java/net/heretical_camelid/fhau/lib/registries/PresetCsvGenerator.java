package net.heretical_camelid.fhau.lib.registries;

import java.io.PrintStream;

class PresetCsvGenerator implements PresetRegistry.Visitor {
    private static final String DATA_LINE_FORMAT = "%4d,%-16s,%-20s,%-20s,%-20s,%-20s,%-20s\n";
    private static final String HEADER_LINE_FORMAT = DATA_LINE_FORMAT.replace("%4d","%4s");
    String m_outputPath;
    StringBuilder m_stringBuilder;


    PresetCsvGenerator(String outputPath) {
        m_outputPath = outputPath;
        m_stringBuilder = new StringBuilder();
    }

    @Override
    public void visitBeforeRecords(PresetRegistry registry) {
        m_stringBuilder.append(String.format(
            HEADER_LINE_FORMAT,
            "slot","name", "stomp","mod","amp","delay","reverb"
        ));
    }
    @Override
    public void visitRecord(int slotIndex, Object record) {
        PresetRecord prb = (PresetRecord) record;
        assert prb != null;
        m_stringBuilder.append(String.format(
            DATA_LINE_FORMAT,
            slotIndex,
            prb.displayName(),
            prb.moduleName("stomp"),
            prb.moduleName("mod"),
            prb.moduleName("amp"),
            prb.moduleName("delay"),
            prb.moduleName("reverb")
        ));
    }

    @Override
    public void visitAfterRecords(PresetRegistry registry) {
        PresetRegistry.outputToFile(
            m_outputPath, m_stringBuilder.toString()
        );
    }
}
