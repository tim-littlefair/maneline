package net.heretical_camelid.maneline.lib.utilities;

import org.json.JSONArray;
import org.json.JSONObject;

public class PresetJO extends JSONObject {
    public PresetJO(String presetName) {
        JSONObject info = new JSONObject();
        info.put("displayName", presetName);
        this.put("info", info);

        JSONArray audioGraph_Nodes = new JSONArray();
        JSONObject audioGraph = new JSONObject();
        audioGraph.put("nodes",audioGraph_Nodes);
        this.put("audioGraph", audioGraph);
    }
}
