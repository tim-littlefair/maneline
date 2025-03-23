package net.heretical_camelid.fhau.android_app;

import android.util.Pair;
import net.heretical_camelid.fhau.lib.FenderJsonPresetRegistry;
import net.heretical_camelid.fhau.lib.PresetRegistryBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.SortedSet;

public class PresetSuiteManager implements PresetRegistryBase.Visitor {
    final HashMap<String, ArrayList<FenderJsonPresetRegistry.Record>> m_ampPresets;

    PresetSuiteManager(FenderJsonPresetRegistry registry) {
        m_ampPresets = new HashMap<>();
        registry.acceptVisitor(this);
    }

    // The user may want to select a range of presets for export
    // (for example on LT40S to capture only firmware default presets 1-30)
    int m_minSlotIndex = 1;
    int m_maxSlotIndex = 999;
    void setRange(int minSlotIndex, int maxSlotIndex) {
        m_minSlotIndex = minSlotIndex;
        m_maxSlotIndex = maxSlotIndex;
    }

    @Override
    public void visitBeforeRecords(PresetRegistryBase registry) { }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        if(slotIndex<=m_minSlotIndex || slotIndex>=m_maxSlotIndex) {
            return;
        }
        FenderJsonPresetRegistry.Record fjpr = (FenderJsonPresetRegistry.Record) record;
        assert fjpr != null;
        ArrayList<FenderJsonPresetRegistry.Record> presetsForThisAmp = m_ampPresets.get(fjpr.ampName());
        if(presetsForThisAmp==null) {
            presetsForThisAmp = new ArrayList<>();
            presetsForThisAmp.add(fjpr);
            m_ampPresets.put(fjpr.ampName(), presetsForThisAmp);
        } else {
            presetsForThisAmp.add(fjpr);
        }
    }

    @Override
    public void visitAfterRecords(PresetRegistryBase registry) { }

    ArrayList<PresetSuiteEntry> buildPresetSuites(
        int maxPresetsPerSuite,
        int targetPresetsPerSuite,
        int maxAmpsPerSuite
    ) {
        ArrayList<PresetSuiteEntry> retval = new ArrayList<>();
        ArrayList<String> ampNames = new ArrayList<>(m_ampPresets.keySet());
        // First pass - amps which are used often enough for their own suite
        for(String ampName: ampNames) {
            ArrayList<FenderJsonPresetRegistry.Record> presetsForThisAmp = m_ampPresets.get(ampName);
            if(presetsForThisAmp.size()>=targetPresetsPerSuite) {
                retval.add(new PresetSuiteEntry("Amplifier " + ampName, presetsForThisAmp));
            }
        }
        // Second pass - group amps by name (up to maxAmpsPerSuite)
        // Third pass - group other amps A-Z
        return retval;
    }

    static class PresetSuiteEntry
        extends Pair<String, ArrayList<FenderJsonPresetRegistry.Record>> {
        PresetSuiteEntry(
            String suiteName, ArrayList<FenderJsonPresetRegistry.Record> records
        ) {
            super(suiteName, records);
        }
    }
}
