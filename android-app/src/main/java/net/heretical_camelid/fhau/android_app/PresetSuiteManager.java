package net.heretical_camelid.fhau.android_app;

import android.content.res.AssetManager;

import java.io.IOException;

public class PresetSuiteManager {
    final MainActivity m_mainActivity;
    public PresetSuiteManager(MainActivity mainActivity) {
        m_mainActivity = mainActivity;
    }

    void processDay0Suites() {
        AssetManager am = m_mainActivity.getAssets();
        try {
            for(String s: am.list("day0-preset-suites-lt40s")) {
                m_mainActivity.appendToLog(s);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
