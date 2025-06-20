package net.heretical_camelid.fhau.lib.interfaces;

import net.heretical_camelid.fhau.lib.registries.PresetSuiteRegistry;

import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONObject;

public interface IAmpProvider {
    enum ProviderState_e {
        PROVIDER_INITIAL,
        PROVIDER_NO_APPLICABLE_DEVICE,
        PROVIDER_DEVICE_PERMISSION_REQUESTED,
        PROVIDER_CONNECTING_TO_DEVICE,
        PROVIDER_DEVICE_CONNECTION_FAILED,
        PROVIDER_DEVICE_CONNECTION_SUCCEEDED
    }

    PresetSuiteRegistry.PresetSuiteEntry buildPresetSuite(
        String suiteName, ArrayList<HashMap<String, String>> presets
    );

    ArrayList<PresetSuiteRegistry.PresetSuiteEntry> loadCuratedPresetSuites();


    void switchPreset(int slotIndex);

    ProviderState_e  attemptConnection();
}
