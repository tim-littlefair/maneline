package net.heretical_camelid.maneline.lib.presets;

import static java.util.Arrays.sort;

import net.heretical_camelid.maneline.lib.utilities.ResourceLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SignalChain extends ArrayList<DspModule> {

    static public SignalChain create(DspModule[] modules) {
        return new SignalChain(modules);
    }
    private SignalChain(DspModule[] modules) {
        for(DspModule m: modules) {
            if(m==null) {
                continue;
            }
            add(m);
        }
    }

    public String toString() {
        return toString(0,false);
    }

    public String toString(int indent, boolean withDetails) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for(DspModule m: this) {
            sb.append(m.toString(indent, withDetails));
            sb.append(",");
        }
        sb.delete(sb.lastIndexOf(","),sb.length());
        sb.append("]");
        return sb.toString();
    }

    public static void main(String args[]) {
        try {
            DspModule.DspParameterString testString = new DspModule.DspParameterString("dummy");
            assert testString.toString().equals("\"dummy\"") : String.format(
                "testString.toString()=%s", testString.toString()
            );

            DspModule.DspParameterFloat testFloat0 = new DspModule.DspParameterFloat(0.5F + 0.1e-6F);
            // Confirm that toString() renders the value to exactly 6 decimal places
            assert testFloat0.toString().equals("0.500000") : String.format(
                "testFloat0.toString()=%s", testFloat0.toString()
            );
            // Confirm that the stored float variable is rounded to reflect the
            // string rendering
            assert testFloat0.toFloat() == 0.5F : String.format(
                "testFloat0.toFloat()=%f", testFloat0.toFloat()
            );
        }
            catch(Exception e) {
            System.err.println(e.getLocalizedMessage());
            System.exit(1);
        }

        JSONObject testPreset1JO = ResourceLoader.loadJson("/TestPreset1.json");
        JSONObject audioGraph1 = testPreset1JO.getJSONObject("audioGraph");
        JSONArray agNodes1 = audioGraph1.getJSONArray("nodes");
        List<DspModule> modules = new ArrayList<>();
        for(Object node: agNodes1) {
            JSONObject moduleJO = (JSONObject) node;
            String fenderId = moduleJO.getString("FenderId");
            String nodeId = moduleJO.getString("nodeId");
            List<DspModule.DspParameter> params = new ArrayList<DspModule.DspParameter>();
            JSONObject moduleParams = moduleJO.getJSONObject("dspUnitParameters");
            for(String k: moduleParams.keySet()) {
                Object v = moduleParams.get(k);
                final Object canonicalValue;
                final Map<String, Object> valueDetails;
                if(v instanceof JSONObject) {
                    JSONObject vJO = (JSONObject) v;
                    canonicalValue = vJO.getInt("_byteValue");
                    valueDetails = vJO.toMap();
                } else {
                    canonicalValue = v;
                    valueDetails = null;
                }
                params.add(new DspModule.DspParameter(k, canonicalValue, valueDetails));
            }
            modules.add(new DspModule(fenderId, nodeId, params));
        }
        /*
        SignalChain testSC = new SignalChain(List.of(modules));
        String[] testSCRenderings = new String[] {
            testSC.toString(1,true),
            testSC.toString(4,false),
            testSC.toString()
        };
        for(String s: testSCRenderings) {
            System.out.println(s);
            JSONArray scArray = new JSONArray(s);
            assert scArray.length()==5;
        }
         */

        System.exit(0);
    }
}
