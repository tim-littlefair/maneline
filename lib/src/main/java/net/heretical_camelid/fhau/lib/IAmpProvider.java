package net.heretical_camelid.fhau.lib;

public interface IAmpProvider {
    boolean connect();

    void sendCommand(String commandHexString);

    PresetInfo getPresetInfo(PresetInfo requestedPresets);

    public static void main(String[] args) {
        System.out.println("TODO: tests for IAmplifierProvider");
    }
}
