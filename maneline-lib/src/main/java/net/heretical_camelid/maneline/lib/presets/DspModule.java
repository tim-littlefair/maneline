package net.heretical_camelid.maneline.lib.presets;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DspParameterWithDetails {
    final String m_name;
    final Object m_canonicalValue;
    final Map<String,Object> m_valueDetails;

    DspParameterWithDetails(String name, Object canonicalValue, Map<String,Object> valueDetails) {
        m_name = name;
        m_canonicalValue = canonicalValue;
        m_valueDetails = valueDetails;
    }
}

/**
 * DspFloat is subclassed in order to ensure that the
 * value stored rounded to a consistent number of
 * decimal places and output with trailing zeros if
 * necessary to match the storage precision
 */
class DspFloat {
    private static final String _FLOAT_FORMAT = "%.03f";
    Float m_value;
    DspFloat(Float value) {
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

/**
 * DspString is subclassed in order to ensure that the
 * value is enclosed in double quotes when toString() is invoked.
 */
class DspString {
    String m_value;
    DspString(String value) {
        m_value = value;
    }
    @Override
    public String toString() {
        return String.format("\"%s\"", m_value);
    }
}


/**
 * DspModule implements the contract of JSONObject but overrides the
 * behaviour to guarantee that toString() returns parameters in the
 * order they were supplied in the ArrayList passed to the
 * constructor.
 * Note that instances of this this class are immutable.
 */
public class DspModule extends JSONObject {





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
    public final String m_fenderId;
    /**
     * m_nodeId identifies the type of the module.
     * <p>
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
    public final String m_nodeId;

    public final Map<String, Object> m_parameters;

    DspModule(String fenderId, String nodeId, List<DspParameterWithDetails> parameters) {
        m_fenderId = fenderId.replaceAll("\\W+","");
        m_nodeId = nodeId;
        m_parameters = new LinkedHashMap<String, Object>();
        for (DspParameterWithDetails p : parameters) {
            if (p.m_canonicalValue instanceof Float) {
                // before storing, round the value to the precision implied by
                // DspFloat._FLOAT_FORMAT
                m_parameters.put(p.m_name, Float.valueOf(
                    new DspFloat((Float) p.m_canonicalValue).toString())
                );
            } else {
                m_parameters.put(p.m_name, p.m_canonicalValue);
            }
            if (p.m_valueDetails != null) {
                m_parameters.put(p.m_name + "#details", p.m_valueDetails);
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
        Indenter indenter = new Indenter(indent);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(indenter.separatorForLevel(1));
        sb.append("\"FenderId\":" + "\"" + m_fenderId + "\",");
        sb.append(indenter.separatorForLevel(1));
        sb.append("\"nodeId\":" + "\"" + m_nodeId + "\",");
        sb.append(indenter.separatorForLevel(1));
        sb.append("\"dspUnitParameters\":{");
        if (!m_parameters.isEmpty()) {
            for (String k : m_parameters.keySet()) {
                Object v = m_parameters.get(k);
                if (v == null) {
                    continue;
                } else if (!k.endsWith("#details")) {
                    sb.append(indenter.separatorForLevel(2));
                    sb.append("\"" + k + "\":");
                    serializeParamValue(v, sb, true);
                    sb.append(",");
                } else if (withDetails) {
                    sb.append(indenter.separatorForLevel(2));
                    sb.append("\"" + k + "\":");
                    serializeParamValue(v, sb, false);
                    sb.append(",");
                }
            }
            sb.delete(sb.lastIndexOf(","), sb.length());
            //sb.append(indenter.separatorForLevel(2));
        }
        sb.append(indenter.separatorForLevel(1));
        sb.append("}");
        sb.append(indenter.separatorForLevel(0));
        sb.append("}");
        return sb.toString();
    }

    public String typeAndName() {
        return String.join(":",m_nodeId, m_fenderId);
    }

    private void serializeParamValue(Object paramValue, StringBuilder sb, boolean quantizeFloats) {
        if ((paramValue instanceof Float) && (quantizeFloats == true)) {
            sb.append(new DspFloat((Float) paramValue).toString());
        } else if (paramValue instanceof String) {
            sb.append(new DspString((String) paramValue).toString());
        } else if (paramValue instanceof Map) {
            sb.append("{");
            Map<String, Object> paramValueAsMap = (Map<String, Object>) paramValue;
            List<String> paramNamesAndValues = new ArrayList<>(paramValueAsMap.size());
            for (String k : paramValueAsMap.keySet()) {
                Object v = paramValueAsMap.get(k);
                sb.append(new DspString(k).toString());
                sb.append(":");
                serializeParamValue(v, sb, quantizeFloats);
                sb.append(",");
            }
            sb.replace(sb.lastIndexOf(","), sb.length(), "}");
        } else {
            sb.append(paramValue.toString());
        }
    }

    private class Indenter {
        final String m_optNewline;
        final String m_perLevelPrefix;

        Indenter(int spacesPerLevel) {
            if (spacesPerLevel == 0) {
                m_optNewline = "";
                m_perLevelPrefix = "";
            } else {
                m_optNewline = "\n";
                m_perLevelPrefix = String.join(
                    "",
                    Collections.nCopies(spacesPerLevel, " ")
                );
            }
        }

        String separatorForLevel(int indentLevel) {
            return m_optNewline + String.join(
                "",
                Collections.nCopies(indentLevel, m_perLevelPrefix)
            );
        }
    }
}
