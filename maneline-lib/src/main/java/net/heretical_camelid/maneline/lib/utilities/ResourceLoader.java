package net.heretical_camelid.maneline.lib.utilities;

import net.heretical_camelid.maneline.lib.presets.PresetBase;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;

public class ResourceLoader {
    public static JSONObject loadJson(String s) {
        InputStream testPreset1IS = PresetBase.class.getResourceAsStream(s);
        assert testPreset1IS != null;
        byte[] jsonBytes = new byte[10240];
        try {
            int readResult = testPreset1IS.read(jsonBytes);
            assert readResult > 0;
            return new JSONObject(new JSONTokener(new String(jsonBytes)));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
