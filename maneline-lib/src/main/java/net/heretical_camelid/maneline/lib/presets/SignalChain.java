package net.heretical_camelid.maneline.lib.presets;

import static java.util.Arrays.sort;

import net.heretical_camelid.maneline.lib.utilities.ResourceLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

class DspParameter {
    final String m_name;
    final Object m_canonicalValue;
    final Map<String,Object> m_valueDetails;

    DspParameter(String name, Object canonicalValue, Map<String,Object> valueDetails) {
        m_name = name;
        m_canonicalValue = canonicalValue;
        m_valueDetails = valueDetails;
    }
}

/**
 * DspModule implements the contract of JSONObject but overrides the
 * behaviour to guarantee that toString() returns parameters in the
 * order they were supplied in the ArrayList passed to the
 * constructor.
 * Note that instances of this this class are immutable.
 */
class DspModule extends JSONObject {
/*
    enum ModuleType_e {
        // amplifier simulations
        amp,

        // Skip two items to align with discriminant bytes in FenderFUSE
        // on-the-wire protocol
        MT_NOT_USED_1,MT_NOT_USED_2,

        // effect simulations used in Mustang I v2, Mustang LT40S
        stomp, mod, reverb, delay,

        // eq (graphic equalizer) effect seems to appear instead of mod in
        // Rumble LT25 presets,
        // Haven't yet worked out how this is rendered on-the-wire.
        eq,

        // utility modules include the "Passthru" module type which reflects
        // the absence of a module.
        utility,

        // end marker
        MT_LIMIT;
    }
 */

    /**
     * m_fenderId is the name of the module,
     */
    final String m_fenderId;
    /**
     * m_nodeId identifies the type of the module.
     *
     * The majority of (non-empty) modules directly attached to the signal chain
     * for presets targetting 'Mustang' branded amps are of one of the following
     * types:
     * + "amp", "stomp", "mod", "delay", "reverb"
     * These terms are drived from the nodeId parameter in the different-but-related
     * JSON preset formats consumed on-the-wire by post-classic amps in the LT-, GT-,
     * GTX- and MMP ranges.
     * There is also a module type "utility" which includes the "Passthru" module
     * which is supplied as a placeholder in a slot reserved for one of the types
     * above when there is no module of the type the slot is reserved for.
     * Presets for Bass-oriented amps with "Rumble" branding in place of "Mustang"
     * use a module type "eq" (graphic equalizer).
     * The FendertTONE LT Desktop app enforces that all Mustang LT- presets can only
     * be configured with a have signal chain of the form "stomp", "mod",
     * "amp", "delay", "reverb" (all modules except amp are optional),
     * The Rumble LT-25 presets are all "stomp", "amp", "eq", "delay", "reverb".
     * The GT-, GTX-, MMP FenderTONE mobile app allows more freedom in ordering the
     * effects.
     * The 'classic' amp ranges (Mustang I-V original/v2 and various earlier models
     * starting with G-DEC3) have a binary format on-the-wire, but use terms
     * similar to these in the XML-based .fuse file format for saving presets, except
     * the term "Distortion" was used rather than "mod" (but from the names of the modules
     * available, the meaning is the same or very similar).
     * On the classic amps, the order of presets is not locked down as it is on Mustang/Rumble LT.
     */
    final String m_nodeId;

    final Map<String, Object> m_parameters;
    DspModule(String fenderId, String nodeId, List<DspParameter> parameters) {
        m_fenderId = fenderId;
        m_nodeId = nodeId;
        m_parameters = new LinkedHashMap<String, Object>();
        for(DspParameter p: parameters) {
            if(p.m_canonicalValue instanceof Float) {
                // before storing, round the value to the precision implied by
                // DspParameterFloat._FLOAT_FORMAT
                m_parameters.put(p.m_name, Float.valueOf(
                    new DspParameterFloat((Float) p.m_canonicalValue).toString())
                );
            } else {
                m_parameters.put(p.m_name, p.m_canonicalValue);
            }
            if(p.m_valueDetails!=null) {
                m_parameters.put(p.m_name+"#details", p.m_valueDetails);
                }
        }
    }

    @Override
    public String toString() {
        return toString(0, false);
    }

    @Override
    public String toString(int indent) {
        return toString(indent, false);
    }

    public String toString(int indent, boolean withDetails) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        String level0Separator = "";
        String level1Separator = "";
        String level2Separator = "";
        if(indent>0) {
            level0Separator="\n";
            level1Separator="\n" + String.join("", Collections.nCopies(indent," "));
            level2Separator="\n" + String.join("", Collections.nCopies(indent*2," "));
        }
        sb.append(level1Separator);
        sb.append("\"FenderId\":" + "\"" + m_fenderId+"\",");
        sb.append(level1Separator);
        sb.append("\"nodeId\":"+ "\"" + m_nodeId+ "\",");
        sb.append(level1Separator);
        sb.append("\"dspUnitParameters\":{");
        if(!m_parameters.isEmpty()) {
            for(String k: m_parameters.keySet()) {
                Object v = m_parameters.get(k);
                if(v==null) {
                    continue;
                } else if (!k.endsWith("#details") ) {
                    sb.append(level2Separator);
                    sb.append("\"" + k + "\":"+ v.toString() + ",");
                } else if (withDetails) {
                    sb.append(level2Separator);
                    sb.append("\"" + k + "\":"+ v.toString() + ",");
                }
            }
            sb.delete(sb.lastIndexOf(","),sb.length());
            sb.append(level2Separator);
        }
        sb.append("}");
        sb.append(level1Separator);
        sb.append("}");
        return sb.toString();
    }

    private void serializeParamValue(Object paramValue, StringBuilder sb, boolean quantizeFloats) {
        if( (paramValue instanceof Float) && (quantizeFloats==true) ) {
            sb.append(new DspParameterFloat((Float) paramValue).toString());
        } else if(paramValue instanceof String) {
            sb.append(new DspParameterString((String) paramValue).toString());
        } else {
            sb.append(paramValue.toString());
        }
    }
}

public class SignalChain {
    ArrayList<DspModule> m_modules = new ArrayList<>();

    public String toString() {
        return toString(0,false);
    }

    public String toString(int indent, boolean withDetails) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for(DspModule m: m_modules) {
            sb.append(m.toString(indent, withDetails));
            sb.append(",");
        }
        sb.delete(sb.lastIndexOf(","),sb.length());
        sb.append("]");
        return sb.toString();
    }

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
        }
            catch(Exception e) {
            System.err.println(e.getLocalizedMessage());
            System.exit(1);
        }

        JSONObject testPreset1JO = ResourceLoader.loadJson("/TestPreset1.json");
        JSONObject audioGraph1 = testPreset1JO.getJSONObject("audioGraph");
        JSONArray agNodes1 = audioGraph1.getJSONArray("nodes");
        SignalChain testSC = new SignalChain();
        for(Object node: agNodes1) {
            JSONObject moduleJO = (JSONObject) node;
            String fenderId = moduleJO.getString("FenderId");
            String nodeId = moduleJO.getString("nodeId");
            List<DspParameter> params = new ArrayList<DspParameter>();
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
                params.add(new DspParameter(k, canonicalValue, valueDetails));
            }
            testSC.m_modules.add(new DspModule(fenderId, nodeId, params));
        }
        System.out.println(testSC.toString(1,true));
        System.out.println(testSC.toString(4,false));
        System.out.println(testSC.toString());

        System.exit(0);
    }
}
