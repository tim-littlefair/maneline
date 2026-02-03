package net.heretical_camelid.maneline.lib.presets;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

public class TONE_LT_Preset extends PresetBase {

    public static TONE_LT_Preset create(byte[] presetBytes) {
        // System.out.println(new String(presetBytes));
        JSONObject ltJO = new JSONObject(new String(presetBytes));
        // System.out.println(ltJO.toString(4));
        String presetName = ltJO.getJSONObject("info").getString("displayName");
        JSONArray connectionsJA = ltJO.getJSONObject("audioGraph").getJSONArray("connections");
        Map<String,String> connectionsMap = new TreeMap<>();
        for(int i=0; i<connectionsJA.length(); ++i) {
            JSONObject cxnItem = connectionsJA.getJSONObject(i);
            String cxnFromType = cxnItem.getJSONObject("input").getString("nodeId");
            String cxnToType = cxnItem.getJSONObject("output").getString("nodeId");
            if(connectionsMap.containsKey(cxnFromType)) {
                assert connectionsMap.get(cxnFromType).equals(cxnToType);
            } else {
                connectionsMap.put(cxnFromType,cxnToType);
            }
        }
        Map<String,Integer> moduleOrder = new TreeMap<>();
        String nextModuleType = connectionsMap.get("preset");
        int moduleIndex=0;
        while(true) {
            moduleOrder.put(nextModuleType,moduleIndex);
            nextModuleType = connectionsMap.get(nextModuleType);
            if(nextModuleType.equals("preset")) {
                break;
            }
            moduleIndex++;
        }
        ArrayList<DspModule> presetModules = new ArrayList<>();
        JSONArray nodesJA = ltJO.getJSONObject("audioGraph").getJSONArray("nodes");
        for(Object node: nodesJA) {
            assert node instanceof JSONObject;
            JSONObject nodeJO = (JSONObject) node;
            JSONObject paramsJO = nodeJO.getJSONObject("dspUnitParameters");
            ArrayList<DspParameterWithDetails> params = new ArrayList<>();
            for (String k : paramsJO.keySet()) {
                params.add(new DspParameterWithDetails(k, paramsJO.get(k), null));
            }
            DspModule nodeModule = new DspModule(
                nodeJO.getString("FenderId"),
                nodeJO.getString("nodeId"),
                params
            );
            presetModules.add(nodeModule);
        }
        SignalChain signalChain=SignalChain.create(
            presetModules.toArray(new DspModule[presetModules.size()])
        );
        return new TONE_LT_Preset(presetName, presetBytes, signalChain);
    }

    private TONE_LT_Preset(String presetName, byte[] presetBytes, SignalChain signalChain) {
        super(presetName, signalChain, CompanionAppName_e.TONE_DESKTOP_LT_USB, presetBytes);
    }
}
