package net.heretical_camelid.fhau.lib;

public class PresetNameListGenerator implements PresetRegistryVisitor {

    @Override
    public void visit(PresetRegistryBase registry) {
        System.out.println("Presets");
        System.out.println(String.format("%3s %16s", "---", "----------------"));
        System.out.println(String.format("%3s %16s", " # ", "      Name      "));
        System.out.println(String.format("%3s %16s", "---", "----------------"));
    }

    @Override
    public void visit(int slotIndex, Object record) {
        PresetRecordBase prb = (PresetRecordBase) record;
        assert prb != null;
        System.out.println(String.format("%3d %16s", slotIndex, prb.m_name));
    }
}
