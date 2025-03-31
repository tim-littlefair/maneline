package net.heretical_camelid.fhau.lib;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

public class PresetSuiteRegistry implements PresetRegistryBase.Visitor {
    final FenderJsonPresetRegistry m_registry;
    final HashMap<String, HashMap<Integer,FenderJsonPresetRegistry.Record>> m_ampPresets;
    ArrayList<PresetSuiteEntry> m_presetSuites;

    public PresetSuiteRegistry(FenderJsonPresetRegistry registry) {
        m_registry = registry;
        m_ampPresets = new HashMap<>();
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
    public void visitBeforeRecords(PresetRegistryBase registry) {
        m_ampPresets.clear();
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        if(slotIndex<=m_minSlotIndex || slotIndex>=m_maxSlotIndex) {
            return;
        }
        FenderJsonPresetRegistry.Record fjpr = (FenderJsonPresetRegistry.Record) record;
        assert fjpr != null;
        HashMap<Integer,FenderJsonPresetRegistry.Record> presetsForThisAmp = m_ampPresets.get(fjpr.ampName());
        if(presetsForThisAmp==null) {
            presetsForThisAmp = new HashMap<>();
            presetsForThisAmp.put(slotIndex,fjpr);
            m_ampPresets.put(fjpr.ampName(), presetsForThisAmp);
        } else {
            presetsForThisAmp.put(slotIndex,fjpr);
        }
    }

    @Override
    public void visitAfterRecords(PresetRegistryBase registry) { }

    public ArrayList<PresetSuiteEntry> buildPresetSuites(
        int maxPresetsPerSuite,
        int targetPresetsPerSuite,
        int maxAmpsPerSuite
    ) {
        m_registry.acceptVisitor(this);
        ArrayList<PresetSuiteEntry> retval = new ArrayList<>();
        ArrayList<String> ampNames = new ArrayList<>(m_ampPresets.keySet());
        int remainingPresetCount = m_registry.m_records.size();
        while(!ampNames.isEmpty()) {
            // We sort the amplifiers in decreasing order of number
            // of associated presets, then by name
            ampNames.sort(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    int size1 = m_ampPresets.get(o1).size();
                    int size2 = m_ampPresets.get(o2).size();
                    int sizeCompareResult = Integer.valueOf(size1).compareTo(size2);
                    if(sizeCompareResult==0) {
                        return o1.compareTo(o2);
                    } else {
                        return -1 * sizeCompareResult;
                    }
                }
            });
            ArrayList<String> processedAmpNames = new ArrayList<>();

            Iterator<String> ampNameIter = ampNames.iterator();
            while (ampNameIter.hasNext()) {
                String firstAmpName = ampNameIter.next();
                HashMap<Integer, FenderJsonPresetRegistry.Record> presetsForThisSuite = m_ampPresets.get(firstAmpName);
                if (presetsForThisSuite.size() >= targetPresetsPerSuite) {
                    retval.add(new PresetSuiteEntry("Amplifier " + firstAmpName, presetsForThisSuite));
                    processedAmpNames.add(firstAmpName);
                    remainingPresetCount -= presetsForThisSuite.size();
                } else {
                    ArrayList<String> suiteAmpNames = new ArrayList<>();
                    suiteAmpNames.add(firstAmpName);
                    while(ampNameIter.hasNext()) {
                        int maxAdditionalPresets = maxPresetsPerSuite - presetsForThisSuite.size();
                        String nextAmpName = ampNameIter.next();
                        HashMap<Integer, FenderJsonPresetRegistry.Record> presetsForNextAmp = m_ampPresets.get(nextAmpName);
                        if(presetsForNextAmp.size()>maxAdditionalPresets) {
                            System.out.println("Overflow: " + nextAmpName);
                            continue;
                        } else {
                            for(int slotIndex: presetsForNextAmp.keySet()) {
                                presetsForThisSuite.put(
                                    slotIndex, presetsForNextAmp.get(slotIndex)
                                );
                            }
                            suiteAmpNames.add(nextAmpName);
                            System.out.println("Building: " + String.join(",",suiteAmpNames));
                            if (presetsForThisSuite.size()>=targetPresetsPerSuite) {
                                retval.add(new PresetSuiteEntry(
                                    "Amplifiers " + String.join(",",suiteAmpNames),
                                    presetsForThisSuite
                                ));
                                processedAmpNames.addAll(suiteAmpNames);
                                remainingPresetCount -= presetsForThisSuite.size();
                                break;
                            } else if(
                                processedAmpNames.size()>0 &&
                                suiteAmpNames.size()>maxAmpsPerSuite
                            ) {
                                suiteAmpNames.clear();
                                presetsForThisSuite.clear();
                                break;
                            } else {
                                retval.add(new PresetSuiteEntry(
                                    "Amplifiers " + String.join(",",suiteAmpNames),
                                    presetsForThisSuite
                                ));
                                processedAmpNames.addAll(suiteAmpNames);
                                remainingPresetCount -= presetsForThisSuite.size();
                                break;
                            }
                        }
                    }
                    if(processedAmpNames.isEmpty()) {
                        m_presetSuites = retval;
                        return retval;
                    } else {
                        System.out.println(
                            "Processed " + String.join(",",processedAmpNames)
                        );
                        ampNames.removeAll(processedAmpNames);
                        if(remainingPresetCount<=maxPresetsPerSuite) {
                            HashMap<Integer, FenderJsonPresetRegistry.Record> presetsForOtherAmpSuite = new HashMap<>();
                            for(String ampName: ampNames) {
                                HashMap<Integer, FenderJsonPresetRegistry.Record> presetMap = m_ampPresets.get(ampName);
                                for(int slotIndex: presetMap.keySet()) {
                                    presetsForOtherAmpSuite.put(slotIndex, presetMap.get(slotIndex));
                                }
                            }
                            retval.add(new PresetSuiteEntry("Other amplifiers",presetsForOtherAmpSuite));
                            m_presetSuites = retval;
                            return retval;
                        }
                        processedAmpNames.clear();
                        // Iterator invalidated - start at top again
                        ampNameIter = ampNames.iterator();
                    }
                }
            }
        }

        m_presetSuites = retval;
        return retval;
    }

    public String nameAt(int position) {
        return m_presetSuites.get(position).m_suiteName;
    }

    public HashMap<Integer, FenderJsonPresetRegistry.Record> recordsAt(int position) {
        return m_presetSuites.get(position).m_presetRecords;
    }

    public static class PresetSuiteEntry {
        String m_suiteName;
        HashMap<Integer, FenderJsonPresetRegistry.Record> m_presetRecords;
        PresetSuiteEntry(
            String suiteName,
            HashMap<Integer, FenderJsonPresetRegistry.Record> presetRecords
        ) {
            m_suiteName = suiteName;
            m_presetRecords = presetRecords;
        }

        public String name() {
            return m_suiteName;
        }
    }

    public static String buttonLabel(int slotIndex, String displayName) {
        assert displayName.length()==16;
        final String ZWNBS = "\uFEFF"; // Unicode zero width no-break space
        String line1=displayName.substring(0,8).strip();
        if(line1.length()==0) {
            line1 = ZWNBS;
        }
        String line2=displayName.substring(8,16).strip();
        if(line2.length()==0) {
            line2 = ZWNBS;
        }
        return String.format("%03d\n%s\n%s",slotIndex,line1,line2);
    }

    public void dump() {
        System.out.println("Preset Suites");
        for(PresetSuiteEntry pse: m_presetSuites) {
            System.out.println(pse.m_suiteName);
            ArrayList<Integer> presetSlots = new ArrayList<>(pse.m_presetRecords.keySet());
            presetSlots.sort(null);
            for(int i: presetSlots) {
                System.out.println(String.format(
                    "    %03d %s", i, pse.m_presetRecords.get(i).displayName()
                ));
            }
        }
    }
}
