package net.heretical_camelid.maneline.lib.registries;
import net.heretical_camelid.maneline.lib.presets.DspModule;
import net.heretical_camelid.maneline.lib.presets.FUSE_Classic_Preset;
import net.heretical_camelid.maneline.lib.presets.PresetBase;
import net.heretical_camelid.maneline.lib.presets.SignalChain;
import net.heretical_camelid.maneline.lib.presets.TONE_LT_Preset;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PresetRecord {

    public static final String COMPANION_APP_FUSE = "fuse";
    public static final String COMPANION_APP_TONE_LT_DESKTOP = "tone-usb";
    public static final String COMPANION_APP_TONE_MOBILE = "tone-ble";
    final String m_name;
    public final PresetBase m_preset;
    String m_audioHash = null;

    public PresetRecord(String name, byte[] definitionBytes, String companionAppName) {
        m_name = name;
        if(companionAppName.equals(COMPANION_APP_FUSE)) {
            m_preset = FUSE_Classic_Preset.create(definitionBytes);
        } else if (companionAppName.equals(COMPANION_APP_TONE_LT_DESKTOP)) {
            m_preset = TONE_LT_Preset.create(definitionBytes);
        } else if (companionAppName.equals(COMPANION_APP_TONE_MOBILE)) {
            throw new UnsupportedOperationException("\n".join(
                "Maneline is not yet interoperable with Mustang Micro Plus, GT-, GTX-, or LTX-",
                "Support for these models is on the backlog"
            ));
        } else {
            throw new UnsupportedOperationException(String.format(
                "No appropriate preset subclass exists for companion app '%s'",
                companionAppName
            ));
        }
    }

    public String displayName() {
        return m_preset.displayName();
    }

    public String moduleTypeAndName(int i) {
        DspModule m = m_preset.signalChain().get(i);
        if(m!=null) {
            return m.typeAndName();
        } else {
            return null;
        }
    }
/*
    public String moduleName(String whichModule) {
        for (Object nodeAsObject : m_preset.signalChain()) {
            if(nodeAsObject==null) {
                continue;
            } else if (nodeAsObject==JSONObject.NULL) {
                continue;
            }
            JSONObject node = (JSONObject) nodeAsObject;
            if(node==null) {
                return "-";
            } else if (false && node.getString("nodeId").equals(whichModule)) {
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
*/
    public String audioHash() {
        // Calculate on first call and retain for future calls
        if (m_audioHash == null) {
            // Otherwise we calculate it and store it for future reference.
            m_audioHash = PresetRegistry.stringHash(
                m_preset.signalChain().toString(),7
            );
        }
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
            default:
                separator = "\n";
                break;
        }
        StringBuilder sb = new StringBuilder();
        boolean insertSeparator = false;
        for (DspModule dspModule: m_preset.signalChain()) {
            if(dspModule.m_isPassthru) {
                continue;
            }
            if(insertSeparator) {
                sb.append(separator);
            }
            sb.append(dspModule.typeAndName());
            if(levelOfDetails!=EffectsLevelOfDetails.MODULES_ONLY) {
                sb.append("(");
                List<String> param_list = new ArrayList<String>();
                for(String k: dspModule.m_parameters.keySet()) {
                    if(k.startsWith("bypass")) {
                        continue;
                    }
                    if(k.endsWith("#details")) {
                        continue;
                    }
                    Object v=dspModule.m_parameters.get(k);
                    if(k==null) {
                        continue;
                    }
                    param_list.add(String.format("%s=%s", k, v));
                }
                sb.append(String.join(",",param_list));
                sb.append(")");
            }
            insertSeparator = true;
        }
        return sb.toString();
    }

    public String shortInfo() {
        StringBuilder sb = new StringBuilder();
        /*
        if (
            m_preset.info.author==null ||
            m_preset.info.author.isEmpty()
        ) {
            sb.append("no author, ");
        } else {
            sb.append("author:" + m_preset.info.author + ", ");
        }
        if (
            m_preset.info.source_id == null ||
            m_preset.info.source_id.isEmpty()
        ) {
            sb.append("no source_id, ");
        } else {
            sb.append("source_id:" + m_preset.info.source_id + ", ");
        }
        sb.append("product_id:" + m_preset.info.product_id + ", ");
        sb.append("is_factory_default:" + m_preset.info.is_factory_default);
         */

        return sb.toString();
    }
}
