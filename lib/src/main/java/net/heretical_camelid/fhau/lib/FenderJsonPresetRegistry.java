package net.heretical_camelid.fhau.lib;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * PresetRegistryBase is a minimal registry of presets which maintains
 * a collection of simple preset objects which consist of a slot number
 * and name only.
 * Both the registry class and the simple preset objects can be extended
 * to support more complex behaviour.
 * Note that the base registry class is responsible for creating the
 * record objects it stores.  Classes which extend the base registry
 * class to are expected to create record objects which contain additional
 * data beyond the slot index and name, so the name, of a more Extended implementations of the registry are expected
 *
 */
public class FenderJsonPresetRegistry extends PresetRegistryBase {
    final String m_outputPath;
    HashMap<String, ArrayList<Integer>> m_duplicateSlots;

    public FenderJsonPresetRegistry(String outputPath) {
        m_outputPath = outputPath;
        m_duplicateSlots = new HashMap<>();
    }

    @Override
    public void register(int slotIndex, String name, byte[] definition) {
        // Slots are numbered from 1
        assert slotIndex > 0;
        // This registry requires a definition
        assert definition != null;

        FenderJsonPresetRecord newRecord = new FenderJsonPresetRecord(name, definition);
        String duplicateSlotKey = String.format(
            "name='%s' hash=%s",
            newRecord.displayName(),newRecord.audioHash()
        );
        ArrayList<Integer> existingDuplicateSlotList = m_duplicateSlots.get(duplicateSlotKey);
        if(existingDuplicateSlotList==null) {
            ArrayList<Integer> newDuplicateSlotList = new ArrayList<>();
            newDuplicateSlotList.add(slotIndex);
            m_duplicateSlots.put(duplicateSlotKey,newDuplicateSlotList);
            m_records.put(slotIndex, newRecord);
        } else {
            existingDuplicateSlotList.add(Integer.valueOf(slotIndex));
        }
    }

    public void generatePresetDetails(PrintStream printStream) {
        acceptVisitor(new PresetDetailsTableGenerator(printStream));
    }

    @Override
    public void dump() {
        if(m_outputPath == null) {
            generatePresetDetails(System.out);
        } else {
            generatePresetDetails(System.out);
            AmpBasedPresetSuiteExporter abpse = new AmpBasedPresetSuiteExporter(System.out);
            // For the moment we only want to create suites containing the non-empty
            // default firmware presets at slots 1 to 30.
            abpse.setRange(1,30);
            acceptVisitor(abpse);
            abpse.writePresetSuites(m_outputPath);
        }
    }

    /**
     * non-public class FenderJsonPresetRecord requires a hash function
     * in order to enable consumers of the preset details report or
     * the amp-based preset suite JSON files to determine whether the
     * audio parameters of a preset have been modified relative to a
     * report or suite from a different amp or the same amp at an earlier
     * point in time.
     * @param prefixLength the length of the hash in characters
     * @return the last prefixLength characters of the hex encoded
     * SHA-256 hash of the string.
     */
    public static String stringHash(String inputString, int prefixLength) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(inputString.getBytes(StandardCharsets.UTF_8));
            String mdHexString = new BigInteger(md.digest()).toString(16);
            return mdHexString.substring(mdHexString.length()-prefixLength, mdHexString.length());
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(inputString.hashCode()).substring(0, prefixLength);
        }
    }
}

class FenderJsonPresetRecord extends PresetRecordBase {
    final String m_definitionRawJson;
    final JsonObject m_definitionJsonObject;

    public FenderJsonPresetRecord(String name, byte[] definitionBytes) {
        super(name);
        m_definitionRawJson = new String(definitionBytes, StandardCharsets.UTF_8);
        m_definitionJsonObject = JsonParser.parseString(
            m_definitionRawJson
        ).getAsJsonObject();
    }

    public String getValue(String itemJsonPath) {
        JsonElement je = m_definitionJsonObject;
        String[] pathElements = itemJsonPath.split("/");
        for(String pe: pathElements) {
            if("01234".contains(pe)) {
                JsonArray joAsArray = je.getAsJsonArray();
                assert joAsArray != null;
                je = joAsArray.get(Integer.parseInt(pe));
            } else {
                je = je.getAsJsonObject().get(pe);
            }
            if(je==null) {
                return null;
            }
        }
        try {
            return je.getAsString();
        }
        catch(IllegalStateException e) {
            return je.toString();
        }
    }

    public String displayName() {
        return getValue("info/displayName");
    }

    public String ampName() {
        // For the firmware presets 1-30 the amp is always at node #2
        // This is not always true of presets uploaded from Fender
        // via FenderTone (amp at node #0 being the most common exception
        // I've seen).
        // For efficiency we search node 2 first, then the other
        // nodes in numeric order.
        for(int nodeIndex: new int[] { 2, 0, 1, 3, 4 }) {
            String nodePrefix=String.format("audioGraph/nodes/%d/",nodeIndex);
            String nodeId=getValue(nodePrefix+"nodeId");
            if(nodeId.equals("amp")) {
                String jsonAmpName = getValue(nodePrefix + "FenderId");
                return jsonAmpName.replace("DUBS_","");
            }
        }
        return null;
    }

    public String audioHash() {
        // All audio parameters of the preset are encodded in the audioGraph submap,
        // which has two subkeys, 'nodes' and 'connections'.

        // We examine the 'connections' item first because we are not sure
        // whether it is even possible for this to change.
        String cxnsJson = getValue("audioGraph/connections");
        String cxnsHash = FenderJsonPresetRegistry.stringHash(cxnsJson,3);

        String nodesJson = getValue("audioGraph/nodes");
        String nodesHash = FenderJsonPresetRegistry.stringHash(nodesJson,3);
        return String.format("%s-%s", cxnsHash, nodesHash);
    }

    public String dspUnitDesc(int nodeIndex) {
        String nodePrefix = String.format("audioGraph/nodes/%d/",nodeIndex);
        String nodeType = getValue(nodePrefix+"nodeId");
        if(nodeType.equals("amp")) {
            return "$AMP$";
        }
        String nodeName = getValue(nodePrefix+"FenderId").replace("DUBS_","");
        if(nodeName.equals("Passthru")) {
            return null;
        }
        return nodeType + ":" + nodeName;
    }

    public String effects() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; ++i) {
            String nextUnit = dspUnitDesc(i);
            if (nextUnit == null) {
                continue;
            }
            sb.append(nextUnit);
            if (i < 5) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
}

class PresetDetailsTableGenerator implements PresetRegistryVisitor {
    private final static String _LINE_FORMAT = "%3d %-16s %-20s %-7s %-60s";
    PrintStream m_printStream;
    PresetDetailsTableGenerator(PrintStream printStream) {
        m_printStream = printStream;
    }
    @Override
    public void visitBeforeRecords(PresetRegistryBase registry) {
        m_printStream.println();
        m_printStream.println("Unique Presets");
        m_printStream.println(String.format(
            _LINE_FORMAT.replace("%3d", "%3s"),
            "#", "Name", "Amplifier","Hash", "Effect Chain"
        ));
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        FenderJsonPresetRecord fjpr = (FenderJsonPresetRecord) record;
        assert fjpr != null;
        m_printStream.println(String.format(
            _LINE_FORMAT,
            slotIndex, fjpr.displayName(), fjpr.ampName(), fjpr.audioHash(), fjpr.effects()
        ));
    }

    @Override
    public void visitAfterRecords(PresetRegistryBase registry) {
        FenderJsonPresetRegistry fjpRegistry = (FenderJsonPresetRegistry) registry;
        assert fjpRegistry!=null;
        m_printStream.println();
        m_printStream.println("Duplicated Presets");
        for(String duplicateKey: fjpRegistry.m_duplicateSlots.keySet()) {
            ArrayList<Integer> duplicateSlotList = fjpRegistry.m_duplicateSlots.get(duplicateKey);
            if(duplicateSlotList.size()==1) {
                continue;
            }
            m_printStream.println(String.format(
                "Preset %3d (%s) is duplicated at the following slots: %s",
                duplicateSlotList.get(0).intValue(), duplicateKey, duplicateSlotList
            ));
        }
    }
}

class AmpBasedPresetSuiteExporter implements PresetRegistryVisitor {
    HashMap<String,JsonObject> m_ampPresetSuites;

    // The user may want to select a range of presets for export
    // (for example on LT40S to capture only firmware default presets 1-30)
    int m_minSlotIndex = 1;
    int m_maxSlotIndex = 999;
    AmpBasedPresetSuiteExporter(PrintStream printStream) {
        m_ampPresetSuites = new HashMap<>();
    }

    void setRange(int minSlotIndex, int maxSlotIndex) {
        m_minSlotIndex = minSlotIndex;
        m_maxSlotIndex = maxSlotIndex;
    }

    @Override
    public void visitBeforeRecords(PresetRegistryBase registry) {
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        if(slotIndex<=m_minSlotIndex || slotIndex>=m_maxSlotIndex) {
            return;
        }
        FenderJsonPresetRecord fjpr = (FenderJsonPresetRecord) record;
        assert fjpr != null;
        JsonObject suiteForThisAmp = m_ampPresetSuites.get(fjpr.ampName());
        if(suiteForThisAmp==null) {
            suiteForThisAmp = new JsonObject();
            suiteForThisAmp.addProperty("ampName", fjpr.ampName());
            suiteForThisAmp.add("presets", new JsonArray());
            m_ampPresetSuites.put(fjpr.ampName(), suiteForThisAmp);
        }
        JsonArray presetArray = suiteForThisAmp.getAsJsonArray("presets");
        JsonObject newPreset = new JsonObject();
        newPreset.addProperty("slotIndex", slotIndex);
        newPreset.addProperty("presetName", fjpr.displayName());
        newPreset.addProperty("audioHash", fjpr.displayName());
        presetArray.add(newPreset);
    }

    @Override
    public void visitAfterRecords(PresetRegistryBase registry) {

    }

    public void writePresetSuites(String pathPrefix) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        for(String presetName: m_ampPresetSuites.keySet()) {
            String targetPath = pathPrefix + "/" + presetName+".amp_presets.json";
            FileOutputStream fos;
            try {
                 fos = new FileOutputStream(targetPath);
            } catch (FileNotFoundException e) {
                System.err.println("Unable to open " + targetPath + ", continuing...");
                continue;
            }
            String jsonForSuite = gson.toJson(m_ampPresetSuites.get(presetName));
            try {
                fos.write(jsonForSuite.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("Unable to write to " + targetPath + ", continuing...");
                continue;
            }
        }
    }
}

