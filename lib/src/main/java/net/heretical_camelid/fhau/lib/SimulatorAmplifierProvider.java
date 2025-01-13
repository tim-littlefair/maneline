package net.heretical_camelid.fhau.lib;

import java.util.regex.Pattern;

public class SimulatorAmplifierProvider implements IAmplifierProvider {

    @Override
    public boolean connect(StringBuilder sb) {
        sb.append("Simulated device connected\n");
        return true;
    }

    @Override
    public void sendCommand(String commandHexString, StringBuilder sb) {
    }

    @Override
    public void expectReports(Pattern[] reportHexStringPatterns, StringBuilder sb) {

    }

    @Override
    public PresetInfo getPresetInfo(PresetInfo requestedPresets) {
        assert requestedPresets == null;
        PresetInfo retval = new PresetInfo();
        PresetRecord pr1 = new PresetRecord("COOL SOUND",1);
        PresetRecord pr2 = new PresetRecord("WARM SOUND",2);
        retval.add(pr1);
        retval.add(pr2);
        return retval;
    }

    public static void main(String[] args) {
        System.out.println("TODO: tests for SimulatorAmplifierProvider");
    }
}
