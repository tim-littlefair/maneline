package net.heretical_camelid.fhau.lib.registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

public class PresetSuiteRegistry {
    final FenderJsonPresetRegistry m_registry;
    final HashMap<String, HashMap<Integer, FenderJsonPresetRecord>> m_suitePresets;
    ArrayList<PresetSuiteEntry> m_presetSuites;

    public PresetSuiteRegistry(FenderJsonPresetRegistry registry) {
        m_registry = registry;
        m_suitePresets = new HashMap<>();
        m_presetSuites = new ArrayList<>();
    }

    // The user may want to select a range of presets for export
    // (for example on LT40S to capture only firmware default presets 1-30)
    int m_minSlotIndex = 1;
    int m_maxSlotIndex = 999;
    void setRange(int minSlotIndex, int maxSlotIndex) {
        m_minSlotIndex = minSlotIndex;
        m_maxSlotIndex = maxSlotIndex;
    }

    /*
    @Override
    public void visitBeforeRecords(PresetRegistryBase registry) {
        m_suitePresets.clear();
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        if(slotIndex<=m_minSlotIndex || slotIndex>=m_maxSlotIndex) {
            return;
        }
        FenderJsonPresetRecord fjpr = (FenderJsonPresetRecord) record;
        assert fjpr != null;
        HashMap<Integer, FenderJsonPresetRecord> presetsForThisAmp = m_suitePresets.get(fjpr.ampName());
        if(presetsForThisAmp==null) {
            presetsForThisAmp = new HashMap<>();
            presetsForThisAmp.put(slotIndex,fjpr);
            m_suitePresets.put(fjpr.ampName(), presetsForThisAmp);
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
        ArrayList<String> ampNames = new ArrayList<>(m_suitePresets.keySet());
        int remainingPresetCount = m_registry.m_records.size();
        while(!ampNames.isEmpty()) {
            // We sort the amplifiers in decreasing order of number
            // of associated presets, then by name
            ampNames.sort(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    int size1 = m_suitePresets.get(o1).size();
                    int size2 = m_suitePresets.get(o2).size();
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
                HashMap<Integer, FenderJsonPresetRecord> presetsForThisSuite = m_suitePresets.get(firstAmpName);
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
                        HashMap<Integer, FenderJsonPresetRecord> presetsForNextAmp = m_suitePresets.get(nextAmpName);
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
                        ampNames.removeAll(processedAmpNames);
                        if(remainingPresetCount<=maxPresetsPerSuite) {
                            HashMap<Integer, FenderJsonPresetRecord> presetsForOtherAmpSuite = new HashMap<>();
                            for(String ampName: ampNames) {
                                HashMap<Integer, FenderJsonPresetRecord> presetMap = m_suitePresets.get(ampName);
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
     */

    public String nameAt(int position) {
        return m_presetSuites.get(position).m_suiteName;
    }

    public HashMap<Integer, FenderJsonPresetRecord> recordsAt(int position) {
        return m_presetSuites.get(position).m_presetRecords;
    }

    public PresetSuiteEntry createPresetSuiteEntry(
        String suiteName,
        ArrayList<HashMap<String,String>> presets
    ) {
        HashMap<Integer, FenderJsonPresetRecord> presetRecords = new HashMap<>();
        for(HashMap<String,String> presetAttributes: presets) {
            String presetName = presetAttributes.get("presetName");
            String audioHash = presetAttributes.get("audioHash");
            Integer slotIndex = m_registry.findAudioHash(audioHash, presetName);
            if(slotIndex!=null) {
                FenderJsonPresetRecord presetRecord=m_registry.get(slotIndex);
                assert presetRecord!=null;
                System.out.println(String.format(
                     "Preset '%s' found for audioHash='%s'",
                     presetName, audioHash
                ));
                presetRecords.put(slotIndex,presetRecord);
            } else {
                System.out.println(String.format(
                    "No preset record found for preset '%s' audioHash='%s'",
                    presetName, audioHash
                ));
            }
        }
        if(presetRecords.isEmpty()) {
            System.out.println("No preset records found for suite " + suiteName);
            return null;
        } else {
            PresetSuiteEntry newPSE = new PresetSuiteEntry(suiteName,presetRecords);
            m_presetSuites.add(newPSE);
            m_suitePresets.put(suiteName,newPSE.m_presetRecords);
            return newPSE;
        }
    }

    public static class PresetSuiteEntry {
        String m_suiteName;
        HashMap<Integer, FenderJsonPresetRecord> m_presetRecords;
        PresetSuiteEntry(
            String suiteName,
            HashMap<Integer, FenderJsonPresetRecord> presetRecords
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

    public int firstSuiteForSlotIndex(int slotIndex) {
        for(int i=0; i<m_presetSuites.size(); ++i) {
            for(int j: m_presetSuites.get(i).m_presetRecords.keySet()) {
                if(j==slotIndex) {
                    return i;
                }
            }
        }
        return -1;
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
