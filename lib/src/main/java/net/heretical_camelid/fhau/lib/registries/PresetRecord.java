package net.heretical_camelid.fhau.lib.registries;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;

public class PresetRecord {
    final String m_name;
    final String m_definitionRawJson;
    final PresetCanonicalSerializer m_presetCanonicalSerializer;

    String m_audioHash = null;

    static Gson s_dspParamGson = new GsonBuilder().setVersion(92.0).create();


    public PresetRecord(String name, byte[] definitionBytes) {
        m_name = name;
        m_definitionRawJson = new String(definitionBytes, StandardCharsets.UTF_8);
        m_presetCanonicalSerializer = PresetRegistry.s_gsonCompact.fromJson(
            m_definitionRawJson,
            PresetCanonicalSerializer.class
        );

        // Presets with different histories (i.e. unmodified firmware presets
        // vs presets imported or modified by Fender Tone) can have JSON
        // structures which are identical in meaning but different in
        // element ordering.  We run the makeCanonical function to standardize
        // the sort order of elements in order to ensure that presets which are
        // exact equivalants from an audio PoV generate the same hash code.
        m_presetCanonicalSerializer.makeCanonical();
    }

    public String displayName() {
        return m_presetCanonicalSerializer.info.displayName;
    }

    public String ampName() {
        for (
            PresetCanonicalSerializer.PCS_Node node :
            m_presetCanonicalSerializer.audioGraph.nodes
        ) {
            if (node.nodeId.equals("amp")) {
                return node.FenderId.replace("DUBS_", "");
            }
        }
        return null;
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
            PresetCanonicalSerializer.PCS_Node node :
            m_presetCanonicalSerializer.audioGraph.nodes
        ) {
            String nextNodeType = node.nodeId;
            String nodeName = node.FenderId.replace("DUBS_", "");
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
                    String paramString = s_dspParamGson.toJson(node.dspUnitParameters);
                    // paramString = paramString.substring(1,paramString.length()-2);
                    // strip leading and trailing braces and quotes
                    paramString = paramString.replaceAll("[{}\"]","");
                    sb.append("(");
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
        if (m_presetCanonicalSerializer.info.author.isEmpty()) {
            sb.append("no author, ");
        } else {
            sb.append("author:" + m_presetCanonicalSerializer.info.author + ", ");
        }
        if (m_presetCanonicalSerializer.info.source_id.isEmpty()) {
            sb.append("no source_id, ");
        } else {
            sb.append("source_id:" + m_presetCanonicalSerializer.info.source_id + ", ");
        }
        sb.append("product_id:" + m_presetCanonicalSerializer.info.product_id + ", ");
        sb.append("is_factory_default:" + m_presetCanonicalSerializer.info.is_factory_default);

        return sb.toString();
    }
}
