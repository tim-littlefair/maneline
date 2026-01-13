package net.heretical_camelid.maneline.lib.registries;
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

    public PresetRecord(PresetJO presetJO, byte[] definitionBytes) {
        m_presetJO = presetJO;
        m_name = presetJO.displayName();
        m_rawDefinition = new String(definitionBytes, StandardCharsets.UTF_8);
    }

    public PresetRecord(String name, byte[] definitionBytes) {
        m_name = name;
        m_rawDefinition = new String(definitionBytes, StandardCharsets.UTF_8);
        m_presetJO = new PresetJO(m_rawDefinition);
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

    public String exportBasename() {
        return String.format(
            "%s-%s",
            displayName().strip().replaceAll("\\W+","_"),
            audioHash()
        );
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
        return m_presetJO.toString(4);
    }
}
