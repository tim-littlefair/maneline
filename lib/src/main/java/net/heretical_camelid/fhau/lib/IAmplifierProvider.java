package net.heretical_camelid.fhau.lib;

import java.util.regex.Pattern;

public interface IAmplifierProvider {
    boolean connect(
        StringBuilder sb
    );

    void sendCommand(String commandHexString, StringBuilder sb);

    void expectReports(Pattern[] reportHexStringPatterns, StringBuilder sb);

    PresetInfo getPresetInfo(PresetInfo requestedPresets);

    public static void main(String[] args) {
        System.out.println("TODO: tests for IAmplifierProvider");
    }
}
