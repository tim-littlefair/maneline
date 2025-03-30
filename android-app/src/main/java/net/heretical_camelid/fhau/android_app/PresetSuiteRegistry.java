package net.heretical_camelid.fhau.android_app;

import android.util.Pair;
import net.heretical_camelid.fhau.lib.FenderJsonPresetRegistry;
import net.heretical_camelid.fhau.lib.PresetRegistryBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class PresetSuiteRegistry implements PresetRegistryBase.Visitor {
    final HashMap<String, ArrayList<FenderJsonPresetRegistry.Record>> m_ampPresets;
    ArrayList<PresetSuiteEntry> m_presetSuites;

    PresetSuiteRegistry(MainActivity mainActivity, FenderJsonPresetRegistry registry) {
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
        Iterator<String> ampNameIter = ampNames.iterator();
        while(ampNameIter.hasNext()) {
            String ampName = ampNameIter.next();
            ArrayList<FenderJsonPresetRegistry.Record> presetsForThisAmp = m_ampPresets.get(ampName);
            if(presetsForThisAmp.size()>=targetPresetsPerSuite) {
                retval.add(new PresetSuiteEntry("Amplifier " + ampName, presetsForThisAmp));
            }
            ampNameIter.remove();
        }
        // Second pass - group amps by name (up to maxAmpsPerSuite)
        boolean anotherGroupFound;
        do {
            ArrayList<String> theseAmpNames = new ArrayList<>();
            ArrayList<FenderJsonPresetRegistry.Record> presetsForTheseAmps = new ArrayList<>();
            anotherGroupFound = false;
            Iterator<String> groupFirstAmpNameIter = ampNames.iterator();
            Iterator<String> groupLastAmpNameIter = ampNames.iterator();
            while(groupLastAmpNameIter.hasNext()) {
                System.out.println(theseAmpNames);
                System.out.println(presetsForTheseAmps);
                String nextAmpName = groupLastAmpNameIter.next();
                System.out.println("Examining " + nextAmpName);
                ArrayList<FenderJsonPresetRegistry.Record> presetsForNextAmp = m_ampPresets.get(nextAmpName);
                int newGroupedPresetCount = presetsForTheseAmps.size() + presetsForNextAmp.size();
                if (newGroupedPresetCount >= targetPresetsPerSuite) {
                    theseAmpNames.add(nextAmpName);
                    presetsForTheseAmps.addAll(presetsForNextAmp);
                    retval.add(new PresetSuiteEntry(
                        "Amplifiers " + String.join(",",theseAmpNames),
                        presetsForTheseAmps
                    ));
                    while(groupFirstAmpNameIter.hasNext()) {
                        groupFirstAmpNameIter.remove();
                        if(groupFirstAmpNameIter==groupLastAmpNameIter) {
                            // Last item in group, remove and start a new group
                            groupFirstAmpNameIter.remove();
                            String suiteName = "Amplifiers " + String.join(",", theseAmpNames);
                            retval.add(new PresetSuiteEntry(suiteName, presetsForTheseAmps));
                            theseAmpNames = new ArrayList<>();
                            presetsForTheseAmps = new ArrayList<>();
                            anotherGroupFound=true;
                            groupFirstAmpNameIter = ampNames.iterator();
                            groupLastAmpNameIter = ampNames.iterator();
                            break;
                        }
                    }
                } else if(theseAmpNames.size()==maxAmpsPerSuite) {
                    // Didn't make it to the target size
                    // Keep only the last amp checked
                    groupFirstAmpNameIter = groupLastAmpNameIter;
                    theseAmpNames = new ArrayList<>();
                    theseAmpNames.add(nextAmpName);
                    presetsForTheseAmps = new ArrayList<>();
                    presetsForTheseAmps.addAll(presetsForNextAmp);
                }
            }
        } while(anotherGroupFound==true);

        // Third pass - group other amps A-Z
        // TBD

        m_presetSuites = retval;

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

    static String buttonLabel(int slotIndex, String displayName) {
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

    static interface Visitor {

    }
}
