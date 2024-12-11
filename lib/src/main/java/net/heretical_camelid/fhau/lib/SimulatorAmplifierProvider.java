package net.heretical_camelid.fhau.lib;

public class SimulatorAmplifierProvider implements IAmplifierProvider {

    @Override
    public boolean connect(StringBuilder sb) {
        sb.append("Simulated device connected\n");
        return true;
    }

    @Override
    public byte[] sendCommandAndReceiveResponse(String commandHexString, StringBuilder sb) {
        return new byte[0];
    }

    @Override
    public PresetInfo getPresets(PresetInfo requestedPresets) {
        assert requestedPresets == null;
        PresetInfo retval = new PresetInfo();
        PresetInfo.PresetRecord pr1 = new PresetInfo.PresetRecord("COOL SOUND",1);
        PresetInfo.PresetRecord pr2 = new PresetInfo.PresetRecord("WARM SOUND",2);
        retval.add(pr1);
        retval.add(pr2);
        return retval;
    }
}
