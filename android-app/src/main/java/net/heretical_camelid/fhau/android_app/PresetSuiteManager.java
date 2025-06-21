package net.heretical_camelid.fhau.android_app;

import android.content.res.AssetManager;

import net.heretical_camelid.fhau.lib.registries.PresetRegistry;
import net.heretical_camelid.fhau.lib.registries.SuiteRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class PresetSuiteManager {
    final MainActivity m_mainActivity;
    private final String m_presetSuiteDir;

    public PresetSuiteManager(MainActivity mainActivity) {
        m_mainActivity = mainActivity;
        m_presetSuiteDir = "day0-preset-suites-lt40s";
    }

    ArrayList<SuiteRegistry.PresetSuiteEntry>  processDay0Suites(
        AndroidUsbAmpProvider provider
    ) {
        PresetRegistry registry = provider.getPresetRegistry();
        ArrayList<SuiteRegistry.PresetSuiteEntry> presetSuites = new ArrayList<>();
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
                SuiteRegistry.PresetSuiteEntry pse = provider.buildPresetSuite(
                    suiteName,repackPresets(psJsonObject.getJSONArray("presets"))
                );
                if(pse!=null) {
                    presetSuites.add(pse);
                    suiteNames.add(suiteName);
                }
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
