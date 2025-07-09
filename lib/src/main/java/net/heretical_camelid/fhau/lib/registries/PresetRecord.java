package net.heretical_camelid.fhau.lib.registries;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PresetRecord {

    final String m_name;
    final String m_definitionRawJson;
    final PresetCanonicalSerializer m_presetCanonicalSerializer;
    String m_audioHash = null;

    // Function prettyJson() returns a JSON rendering of the preset which is
    // mostly pretty-printed, but the uninteresting 'connections' array node (for
    // which the effective content never changes) is rendered in a
    // more compact format with one line for each of the 12 items in the array.
    // + the pretty serialization is done by s_jsonSerializer; and
    // + CONNECTION_ITEM_REGEX is used to build s_cxnItemPattern which
    //   matches the multiline array items and enables them to be compacted.
    private static final Gson s_jsonSerializer = new GsonBuilder().setPrettyPrinting().create();
    public static final String CONNECTION_ITEM_REGEX = "\\{\\s+\"input\":\\s+\\{[^}]+\\},\\s+\"output\":\\s+\\{[^}]+\\}\\s+}";
    private final Pattern s_cxnItemPattern = Pattern.compile(CONNECTION_ITEM_REGEX);

    // function effects(...) uses the serializer below to render a single-line list of effect
    // names and values.  As well as being used to report preset parameters back to the user,
    // this function is used in composition of the value returned by audioHash() which is
    // used to determine whether the audio settings of a preset constitute an exact copy
    // of another preset in the same amp.
    // The .setVersion(92.0) qualifier on the builder is used with the @Since(99.99) annotations
    // on four of the possible parameters in PresetCanonicalSerializer.PCS_DspUnitParameters
    // to filter them out, as these four parameters take on different values according to whether
    // the preset was loaded from factory firmware or was copied using the controls on the amp or
    // the desktop Fender Tone app.  The intent of the filtering is to ensure that copied presets
    // are treated as duplicates of the original unless at least one _intended_ parameter change
    // has been introduced since the copy operation.
    // TODO: Investigate further and replace the filtering behaviour with search and replace
    // TODO: to align the serialized value to the copied form.
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
        return moduleName("amp");
    }

    public String moduleName(String whichModule) {
        for (
            PresetCanonicalSerializer.PCS_Node node :
            m_presetCanonicalSerializer.audioGraph.nodes
        ) {
            if (node.nodeId.equals(whichModule)) {
                return node.FenderId.
                    // LT40S prefix
                    replace("DUBS_", "").
                    // MMP prefix
                    replace("ACD_", "").
                    // placeholder where no module selected
                    replace("Passthru","");
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
        if (
            m_presetCanonicalSerializer.info.author==null ||
            m_presetCanonicalSerializer.info.author.isEmpty()
        ) {
            sb.append("no author, ");
        } else {
            sb.append("author:" + m_presetCanonicalSerializer.info.author + ", ");
        }
        if (
            m_presetCanonicalSerializer.info.source_id == null ||
            m_presetCanonicalSerializer.info.source_id.isEmpty()
        ) {
            sb.append("no source_id, ");
        } else {
            sb.append("source_id:" + m_presetCanonicalSerializer.info.source_id + ", ");
        }
        sb.append("product_id:" + m_presetCanonicalSerializer.info.product_id + ", ");
        sb.append("is_factory_default:" + m_presetCanonicalSerializer.info.is_factory_default);

        return sb.toString();
    }

    public String prettyJson() {
        String retval = s_jsonSerializer.toJson(
            m_presetCanonicalSerializer
        );
        // The Gson pretty output makes the info and audioGraph nodes readable,
        // but wastes a lot of lines on the connections node which varies a bit
        // in order but not at all in meaning.

        // The rest of this function is dedicated to compacting that part of the
        // output

        // The following regex was built here: https://regex101.com/
        Matcher cxnItemMatcher = s_cxnItemPattern.matcher(retval);
        while(cxnItemMatcher.find()) {
            String matchedString = cxnItemMatcher.group(0);
            String compactedString = matchedString.replaceAll("\\s+", "");
            retval = retval.replace(matchedString, compactedString);
        }
        return retval;
    }
}
