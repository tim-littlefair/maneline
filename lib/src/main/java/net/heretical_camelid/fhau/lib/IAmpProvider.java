package net.heretical_camelid.fhau.lib;

import java.util.regex.Pattern;

public interface IAmpProvider {
    boolean connect();

    void sendCommand(String commandHexString);

    void expectReports(Pattern[] reportHexStringPatterns);

    PresetInfo getPresetInfo(PresetInfo requestedPresets);

    public static void main(String[] args) {
        System.out.println("TODO: tests for IAmplifierProvider");
    }
}
