package net.heretical_camelid.fhau.android_app;

import android.text.TextUtils;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import net.heretical_camelid.fhau.lib.FenderJsonPresetRegistry;
import net.heretical_camelid.fhau.lib.PresetRegistryBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

public class PresetSuiteManager implements PresetRegistryBase.Visitor, AdapterView.OnItemSelectedListener {
    final MainActivity m_mainActivity;
    final HashMap<String, ArrayList<FenderJsonPresetRegistry.Record>> m_ampPresets;
    ArrayList<PresetSuiteEntry> m_presetSuites;

    PresetSuiteManager(MainActivity mainActivity, FenderJsonPresetRegistry registry) {
        m_mainActivity = mainActivity;
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

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        assert m_presetSuites!=null;
        PresetSuiteEntry selectedSuite = m_presetSuites.get(position);
        m_mainActivity.clearPresetButtons();
        ArrayList<FenderJsonPresetRegistry.Record> suitePresetRecords = selectedSuite.second;
        m_mainActivity.appendToLog("Preset suite '" + selectedSuite.first + "' selected");
        for(int i=0; i<suitePresetRecords.size(); ++i) {
            FenderJsonPresetRegistry.Record presetRecord = suitePresetRecords.get(i);
            m_mainActivity.setPresetButton(
                i+1, i+1, buttonLabel(i+1, presetRecord.displayName())
            );
            m_mainActivity.appendToLog(
                presetRecord.displayName().replace("( )+"," ") +
                ": " +  presetRecord.effects()
            );
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        m_mainActivity.clearPresetButtons();
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
        final String retval;
        // We want the button label to be a three-line string
        // The first line will be slotIndex
        // The second and third lines will be taken from displayName
        String[] displayNameWords = TextUtils.split(displayName,Pattern.compile("( )+"));
        switch (displayNameWords.length) {
            case 2:
                retval = String.format(
                    "%03d\n%s\n%s",
                    slotIndex,
                    displayNameWords[0],
                    displayNameWords[1]
                );
                break;
            case 1:
                if (displayNameWords[0].length() > 8) {
                    // This case required to handle "ACOUSTICSIM"
                    retval = String.format(
                        "%03d\n%s\n%s",
                        slotIndex,
                        displayNameWords[0].substring(0, 8),
                        displayNameWords[0].substring(8)
                    );
                } else {
                    // This case required to handle "EMPTY"
                    retval = String.format(
                        "%03d\n%s\n%s",
                        slotIndex,
                        displayNameWords[0],
                        "        "
                    );
                }
                break;
            case 0:
                retval = String.format("%03d\n        \n        ", slotIndex);
                break;
            default:
                assert displayName.length() > 8;
                retval = String.format(
                    "%03d\n%s\n%s",
                    slotIndex,
                    displayName.substring(0, 8),
                    displayName.substring(8)
                );
        }
        System.out.println("X:" + displayNameWords + retval);
        return retval;
    }
}
