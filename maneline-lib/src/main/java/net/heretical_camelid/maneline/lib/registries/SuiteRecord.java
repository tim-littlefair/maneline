package net.heretical_camelid.maneline.lib.registries;

import java.util.Map;
import java.util.Set;

public class SuiteRecord {
    String m_suiteName;
    Map<Integer, PresetRecord> m_presetRecords;

    public SuiteRecord(
        String suiteName,
        Map<Integer, PresetRecord> presetRecords
    ) {
        m_suiteName = suiteName;
        m_presetRecords = presetRecords;
    }

    public String name() {
        return m_suiteName;
    }

    public Set<Integer> getSlotIndices() {
        return m_presetRecords.keySet();
    }
}
