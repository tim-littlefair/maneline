package net.heretical_camelid.fhau.lib;

public interface IAmpProvider {
    void sendCommand(String commandHexString);
    String getFirmwareVersion();
    PresetInfo getPresetInfo(PresetInfo requestedPresets);
    public static void main(String[] args) {
        System.out.println("TODO: tests for IAmplifierProvider");
    }
}
