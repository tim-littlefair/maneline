package net.heretical_camelid.maneline.lib.presets;

import static java.util.Arrays.sort;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * DspParameterString is subclassed in order to ensure that the
 * value is enclosed in double quotes when toString() is invoked.
 */
class DspParameterString {
    String m_value;
    DspParameterString(String value) {
        m_value = value;
    }
    @Override
    public String toString() {
        return String.format("\"%s\"", m_value);
    }
}

/**
 * DspParameterFloat is subclassed in order to ensure that the
 * value stored rounded to a consistent number of
 * decimal places and output with trailing zeros if
 * necessary to match the storage precision
 */
class DspParameterFloat {
    private static final String _FLOAT_FORMAT = "%f";
    Float m_value;
    DspParameterFloat(Float value) {
        m_value = Float.valueOf(String.format(_FLOAT_FORMAT, value));
    }

    @Override
    public String toString() {
        return String.format(_FLOAT_FORMAT, m_value);
    }

    public Float toFloat() {
        return m_value;
    }
}

// All o
class DspParameter {
    final String m_name;
    final Object m_canonicalValue;
    final Map<String,Object> m_valueDetails;

    DspParameter(String name, Object canonicalValue, Map<String,Object> valueDetails) {
        m_name = name;
        m_canonicalValue = canonicalValue;
        m_valueDetails = valueDetails;
    }

    String toString(boolean canonicalValueOnly) {
        if(canonicalValueOnly) {
            return String.format("\"%s\":%s", m_name, m_canonicalValue);
        } else {
            return String.format("\"%s\":%s", m_name, m_valueDetails);
        }
    }
}
class DspModule extends LinkedHashMap {
    final String m_fenderId;
    final String m_moduleType;
    DspModule(String fenderId, String moduleType, List<DspParameter> parameters) {
        m_fenderId = fenderId;
        m_moduleType = moduleType;
        for(DspParameter p: parameters) {
            put(p.m_name, p.m_canonicalValue);
            put(p.m_name+"#details", p.m_valueDetails);
        }
    }
}

public class SignalChain {
    String m_name;
    ArrayList<DspModule> m_modules;

    public static void main(String args[]) {
        try {
            DspParameterString testString = new DspParameterString("dummy");
            assert testString.toString().equals("\"dummy\"") : String.format(
                "testString.toString()=%s", testString.toString()
            );

            DspParameterFloat testFloat0 = new DspParameterFloat(0.5F + 0.1e-6F);
            // Confirm that toString() renders the value to exactly 6 decimal places
            assert testFloat0.toString().equals("0.500000") : String.format(
                "testFloat0.toString()=%s", testFloat0.toString()
            );
            // Confirm that the stored float variable is rounded to reflect the
            // string rendering
            assert testFloat0.toFloat() == 0.5F : String.format(
                "testFloat0.toFloat()=%f", testFloat0.toFloat()
            );

            InputStream testPreset1IS = PresetBase.class.getResourceAsStream("/TestPreset1.json");
            assert testPreset1IS != null;
            byte[] jsonBytes = new byte[10240];
            testPreset1IS.read(jsonBytes);
            JSONObject testPreset1JO = new JSONObject(jsonBytes);
            System.out.println(testPreset1JO.toString(1));
            JSONObject audioGraph1 = testPreset1JO.getJSONObject("audioGraph");
            JSONArray agNodes1 = audioGraph1.getJSONArray("nodes");
            for(Object node: agNodes1) {
                JSONObject moduleJO = (JSONObject) node;
                String fenderId = moduleJO.getString("FenderId");
                String nodeId = moduleJO.getString("nodeId");
                List<DspParameter> params = new ArrayList<DspParameter>();
                for(String k: moduleJO.keySet()) {
                    Object v = moduleJO.get(k);
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
                    params.add(new DspParameter(k, canonicalValue, valueDetails));
                }
            }

            System.exit(0);
        }
        catch(Exception e) {
            System.err.println(e.getLocalizedMessage());
            System.exit(1);

        }
    }
}
