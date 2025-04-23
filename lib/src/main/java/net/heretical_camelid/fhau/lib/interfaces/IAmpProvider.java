package net.heretical_camelid.fhau.lib.interfaces;

import net.heretical_camelid.fhau.lib.registries.PresetSuiteRegistry;

import java.util.ArrayList;

public interface IAmpProvider {

    public static enum ProviderState_e {
        PROVIDER_INITIAL,
        PROVIDER_NO_APPLICABLE_DEVICE,
        PROVIDER_DEVICE_PERMISSION_REQUESTED,
        PROVIDER_CONNECTING_TO_DEVICE,
        PROVIDER_DEVICE_CONNECTION_FAILED,
        PROVIDER_DEVICE_CONNECTION_SUCCEEDED
    }

    ArrayList<PresetSuiteRegistry.PresetSuiteEntry> buildAmpBasedPresetSuites(
        int maxPresetsPerSuite, int targetPresetsPerSuite, int maxAmpsPerSuite
    );

    void switchPreset(int slotIndex);

    ProviderState_e  attemptConnection();
}
