package net.heretical_camelid.fhau.lib.interfaces;

import net.heretical_camelid.fhau.lib.PresetInfo;
import net.heretical_camelid.fhau.lib.registries.FenderJsonPresetRegistry;
import net.heretical_camelid.fhau.lib.registries.PresetSuiteRegistry;

import java.util.ArrayList;

public interface IAmpProvider {
    void switchPreset(int slotIndex);
    ArrayList<PresetSuiteRegistry.PresetSuiteEntry> buildAmpBasedPresetSuites(
        int maxPresetsPerSuite, int targetPresetsPerSuite, int maxAmpsPerSuite
    );

    default ArrayList<PresetSuiteRegistry.PresetSuiteEntry> buildAmpBasedPresetSuites(
        FenderJsonPresetRegistry registry,
        int maxPresetsPerSuite, int targetPresetsPerSuite, int maxAmpsPerSuite
    ) {
        assert registry!=null;
        PresetSuiteRegistry m_presetSuiteRegistry = new PresetSuiteRegistry(registry);
        return m_presetSuiteRegistry.buildPresetSuites(
            maxPresetsPerSuite, targetPresetsPerSuite,maxAmpsPerSuite
        );
    }


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
