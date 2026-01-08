package net.heretical_camelid.maneline.lib.registries;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class PresetRecord {

    final String m_name;
    final String m_rawDefinition;
    // final PresetCanonicalSerializer m_presetJO;
    final PresetJO m_presetJO;
    String m_audioHash = null;

    // Function prettyJson() returns a JSON rendering of the preset which is
    // mostly pretty-printed, but the uninteresting 'connections' array node (for
    // which the effective content never changes) is rendered in a
    // more compact format with one line for each of the 12 items in the array.
    // + the pretty serialization is done by s_jsonSerializer; and
    // + CONNECTION_ITEM_REGEX is used to build s_cxnItemPattern which
    //   matches the multiline array items and enables them to be compacted.
    private static final Gson s_jsonSerializer = new GsonBuilder().setPrettyPrinting().create();

    // function effects(...) uses the serializer below to render a single-line list of effect
    // names and values.  As well as being used to report preset parameters back to the user,
    // this function is used in composition of the value returned by audioHash() which is
    // used to determine whether the audio settings of a preset constitute an exact copy
    // of another preset in the same amp.
    // There two other parameters which are typed as String in .PCS_DspUnitParameters
    // (which can only cope with one type per parameter name), but are sometimes returned
    // from the amp as integer or fixed point numerics.  Before we start sending JSON back
    // to the amp we probably need to reflect the amp's typing for these, which are:
    // + tone - usually either 1 or a fixed point positive number less than 1, except for
    //   the value for the DUBS_VariFuzz stomp module in '60S FUZZ' where it takes the string
    //   'normal' (when this is numeric, trailing zeroes in the mantissa are sometimes present
    //   in the copied presets but absent in the original);
    // + shape - 'sine' for the DUBS_Phaser mod module in 'PHASER_SWIRL', 0 for the
    //   DUBS_SineTremolo mod module in VINTAGE_TREMOLO.
    // Two of the other possible parameters in PresetCanonicalSerializer.PCS_DspUnitParameters
    // take on inconsistent values when a preset is copied to another slot without (intentionally)
    // introducing any changes.  These four parameters are:
    // + bypass - boolean, absent in initial presets, false in copied presets;
    // + bypassType - can be absent, Pre, Post or PostNoPreKill in initial presets, often changes
    // from absent or Pre to Post, changes from Post to Pre less often.
    // TODO: Investigate further and implement search and replace to align the serialized values
    // TODO: to the copied form and to align typing for each parameter exactly to what the
    // TODO: FenderTone LT Desktop sends to the amp for the same module.
    static Gson s_dspParamGson = new GsonBuilder().create();

    public PresetRecord(PresetJO presetJO, byte[] definitionBytes) {
        m_presetJO = presetJO;
        m_name = presetJO.displayName();
        m_rawDefinition = new String(definitionBytes, StandardCharsets.UTF_8);
    }

    public PresetRecord(String name, byte[] definitionBytes) {
        m_name = name;
        m_rawDefinition = new String(definitionBytes, StandardCharsets.UTF_8);
        m_presetJO = new PresetJO(m_rawDefinition);

        // Presets with different histories (i.e. unmodified firmware presets
        // vs presets imported or modified by Fender Tone) can have JSON
        // structures which are identical in meaning but different in
        // element ordering.  We run the makeCanonical function to standardize
        // the sort order of elements in order to ensure that presets which are
        // exact equivalants from an audio PoV generate the same hash code.
        m_presetJO.makeCanonical();
    }

    public String displayName() {
        return m_presetJO.displayName();
    }

    public String ampName() {
        return moduleName("amp");
    }

    public String moduleName(String whichModule) {
        for (
            Object nodeAsObject :
            (JSONArray) PresetJO.getSubObject(
                m_presetJO,
                List.of((Object[]) new String[] {"audioGraph","nodes"})
            )
        ) {
            if(nodeAsObject==null) {
                continue;
            } else if (nodeAsObject==JSONObject.NULL) {
                continue;
            }
            JSONObject node = (JSONObject) nodeAsObject;
            if(node==null) {
                return "-";
            } else if (node.getString("nodeId").equals(whichModule)) {
                return node.getString("FenderId").
                    // LT40S prefix
                    replace("DUBS_", "").
                    // MMP prefix
                    replace("ACD_", "").
                    // suffix on some MMP presets (presumably also GT/GTX)
                    replace("GT", "");
            }
        }
        return "";
    }

    public String audioHash() {
        // Calculate on first call and retain for future calls
        if (m_audioHash != null) {
            return m_audioHash;
        }

        // Otherwise we calculate it and store it for future reference.
        String nodesHash1 = PresetRegistry.stringHash(
            effects(EffectsLevelOfDetails.MODULES_ONLY), 4
        );

        String nodesHash2 = PresetRegistry.stringHash(
            effects(EffectsLevelOfDetails.PARAMETERS_ONLY), 4
        );

        m_audioHash = String.format("%s-%s", nodesHash1, nodesHash2);
        return m_audioHash;
    }

    /**
     * This function generates a string summarizing the
     * DSP unit types of nodes in the audio chain.
     * This string can help to recognize similarities between presets
     * which differ only in parameters, or which share most DSP units.
     *
     * @return string listing types of non-passthru units in the chain
     */
    public enum EffectsLevelOfDetails { MODULES_ONLY, PARAMETERS_ONLY, MODULES_AND_PARAMETERS };
    public String effects(EffectsLevelOfDetails levelOfDetails) {
        final String separator;
        switch(levelOfDetails) {
            case MODULES_ONLY:
                separator =  "\u00A0"; // Unicode non-breaking space
                break;
            case PARAMETERS_ONLY:
            case MODULES_AND_PARAMETERS:
            // Java needs default: to be confident separator is initialized
            default:
                separator = "\n";
                break;
        }
        StringBuilder sb = new StringBuilder();
        boolean insertSeparator = false;
        for (
            Object nodeAsObject :
            (JSONArray) PresetJO.getSubObject(
                m_presetJO,
                List.of((Object[]) new String[]{"audioGraph","nodes"})
            )
        ) {
            if(nodeAsObject==null) {
                continue;
            } else if (nodeAsObject==JSONObject.NULL) {
                continue;
            }
            JSONObject node = (JSONObject) nodeAsObject;
            if(node==null) {
                continue;
            } else if(node.getString("FenderId")==null) {
                node.put("FenderId","?");
            }
            String nextNodeType = node.getString("nodeId");
            String nodeName = node.getString("FenderId").replace("DUBS_", "");
            if (!nodeName.equals("Passthru")) {
                if (insertSeparator) {
                    sb.append(separator);
                }
                if(levelOfDetails!=EffectsLevelOfDetails.PARAMETERS_ONLY) {
                    sb.append(
                        nextNodeType + ":" + nodeName
                    );
                }
                if(levelOfDetails!=EffectsLevelOfDetails.MODULES_ONLY) {
                    sb.append("(");
                    String paramString = node.getJSONObject("dspUnitParameters").toString();

                    // Convert the JSON to a simple comma-separated list
                    paramString = paramString.replaceAll("[{}\"]","");

                    // the 'bypass' and 'bypassType' parameters don't affect the sound
                    // so we fiter these out if present
                    paramString = paramString.replaceAll(",bypass:\\w+","");
                    paramString = paramString.replaceAll(",bypassType:\\w+","");

                    sb.append(paramString);
                    sb.append(")");
                }
                insertSeparator = true;
            }

        }
        return sb.toString();
    }

    public String shortInfo() {
        StringBuilder sb = new StringBuilder();
        /*
        if (
            m_presetJO.info.author==null ||
            m_presetJO.info.author.isEmpty()
        ) {
            sb.append("no author, ");
        } else {
            sb.append("author:" + m_presetJO.info.author + ", ");
        }
        if (
            m_presetJO.info.source_id == null ||
            m_presetJO.info.source_id.isEmpty()
        ) {
            sb.append("no source_id, ");
        } else {
            sb.append("source_id:" + m_presetJO.info.source_id + ", ");
        }
        sb.append("product_id:" + m_presetJO.info.product_id + ", ");
        sb.append("is_factory_default:" + m_presetJO.info.is_factory_default);
         */

        return sb.toString();
    }

    public String prettyJson() {
        String retval = s_jsonSerializer.toJson(
            m_presetJO
        );
        return retval;
    }

    public PresetJO getPresetJO() {
        return m_presetJO;
    }
}
