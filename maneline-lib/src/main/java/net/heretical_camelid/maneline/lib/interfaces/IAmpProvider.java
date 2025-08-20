package net.heretical_camelid.maneline.lib.interfaces;

import net.heretical_camelid.maneline.lib.registries.SuiteRecord;
import net.heretical_camelid.maneline.lib.registries.SuiteRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public interface IAmpProvider {
    SuiteRegistry getSuiteRegistry();

    enum ProviderState_e {
        PROVIDER_INITIAL,
        PROVIDER_NO_APPLICABLE_DEVICE,
        PROVIDER_DEVICE_PERMISSION_REQUESTED,
        PROVIDER_CONNECTING_TO_DEVICE,
        PROVIDER_DEVICE_CONNECTION_FAILED,
        PROVIDER_DEVICE_CONNECTION_SUCCEEDED
    }

    SuiteRecord buildPresetSuite(
        String suiteName, ArrayList<HashMap<String, String>> presets,
        Set<Integer> remainingPresetIndices);

    ArrayList<SuiteRecord> loadCuratedPresetSuites();


    void switchPreset(int slotIndex);

    ProviderState_e  attemptConnection();
}
