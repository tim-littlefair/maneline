package net.heretical_camelid.maneline.android_app;

import android.content.res.AssetManager;

import net.heretical_camelid.maneline.lib.registries.PresetRecord;
import net.heretical_camelid.maneline.lib.registries.PresetRegistry;
import net.heretical_camelid.maneline.lib.registries.SuiteRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class PresetSuiteManager {
    public static final String SUITE_NAME_EVERYTHING_ELSE = "Everything Else";
    final MainActivity m_mainActivity;
    private final String m_presetSuiteDir;

    public PresetSuiteManager(MainActivity mainActivity) {
        m_mainActivity = mainActivity;
        m_presetSuiteDir = "day0-preset-suites-lt40s";
    }

    ArrayList<SuiteRecord>  processDay0Suites(
        AndroidUsbAmpProvider provider
    ) {
        PresetRegistry presetRegistry = provider.getPresetRegistry();
        Set<Integer> remainingPresetIndices = presetRegistry.getUniquePresetIndices();
        ArrayList<SuiteRecord> presetSuites = new ArrayList<>();
        AssetManager am = m_mainActivity.getAssets();
        int itemLayoutId = R.layout.preset_suite_dropdown_item;
        ArrayList<String> suiteNames = new ArrayList<>();
        try {
            for(String presetJsonFilename: am.list(m_presetSuiteDir)) {
                InputStream jsonInputStream = am.open(String.join("/",
                    m_presetSuiteDir,presetJsonFilename
                ));
                byte[] jsonBytes = new byte[jsonInputStream.available()];
                jsonInputStream.read(jsonBytes);
                String jsonString = new String(jsonBytes);
                JSONObject psJsonObject = new JSONObject(jsonString);
                String suiteName = psJsonObject.getString("suiteName");
                SuiteRecord pse = provider.buildPresetSuite(
                    suiteName,repackPresets(
                        psJsonObject.getJSONArray("presets")
                    ),
                    remainingPresetIndices
                );
                if(pse!=null) {
                    presetSuites.add(pse);
                    suiteNames.add(suiteName);
                }
            }
            if(!remainingPresetIndices.isEmpty()) {
                Map<Integer,PresetRecord> remainingPresets = new TreeMap<>();
                for(int i: remainingPresetIndices) {
                    remainingPresets.put(i,provider.getPresetRegistry().get(i));
                }
                SuiteRecord everythingElseSuite = new SuiteRecord(
                    SUITE_NAME_EVERYTHING_ELSE,
                    remainingPresets
                );
                presetSuites.add(everythingElseSuite);
                suiteNames.add(SUITE_NAME_EVERYTHING_ELSE);
                provider.getSuiteRegistry().addSuite(
                    SUITE_NAME_EVERYTHING_ELSE,
                    remainingPresets
                );

            }
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }

        return presetSuites;
    }

    static ArrayList<HashMap<String,String>> repackPresets(JSONArray presets) {
        String[] PRESET_ATTRIBUTE_KEYS = new String[] {
            "presetName", "audioHash",
            "shortInfo", "sourceDevice",
            "effectsSummary", "effectsDetails"
        };
        ArrayList<HashMap<String,String>> retval = new ArrayList<>(presets.length());
        for(int i=0; i<presets.length(); ++i) {
            try {
                JSONObject presetJO = presets.getJSONObject(i);
                HashMap<String,String> presetHM = new HashMap<String,String>();
                for(String key: PRESET_ATTRIBUTE_KEYS) {
                    presetHM.put(key,presetJO.getString(key));
                }
                retval.add(presetHM);
            } catch (JSONException e) {
                System.out.println(e.getLocalizedMessage());
            }
        }
        return retval;
    }
}
