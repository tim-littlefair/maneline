package net.heretical_camelid.fhau.lib;

public interface IAmpProvider {
    public static enum ProviderState_e {
        PROVIDER_INITIAL,
        PROVIDER_NO_APPLICABLE_DEVICE,
        PROVIDER_CONNECTING_TO_DEVICE,
        PROVIDER_DEVICE_CONNECTION_FAILED,
        PROVIDER_DEVICE_CONNECTION_SUCCEEDED
    }

    ProviderState_e  attemptConnection();
    ProviderState_e getState();
    String getFirmwareVersion();
    PresetInfo getPresetInfo(PresetInfo requestedPresets);
    void sendCommand(String commandHexString);
    public static void main(String[] args) {
        System.out.println("TODO: tests for IAmplifierProvider");
    }
}
