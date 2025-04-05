package net.heretical_camelid.fhau.lib.registries;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.heretical_camelid.fhau.lib.PresetRecord;

/**
 * The registry class below is an extension of PresetRegistryBase
 * which is aware of the relationship between LT-series presets and
 * their JSON representation, and uses that awareness to export
 * compact/raw and human-readable JSON statements of the preset
 * content.
 */
public class FenderJsonPresetRegistry extends PresetRegistryBase {
    final static Gson s_gsonCompact = new Gson();
    final static Gson s_gsonPretty = new GsonBuilder().setPrettyPrinting().create();

    final String m_outputPath;
    HashMap<Integer,Record> m_slotsToRecords;
    HashMap<String, ArrayList<Integer>> m_duplicateSlots;


    public FenderJsonPresetRegistry(String outputPath) {
        m_outputPath = outputPath;
        m_slotsToRecords = new HashMap<>();
        m_duplicateSlots = new HashMap<>();
    }

    @Override
    public void register(int slotIndex, String name, byte[] definition) {
        // Slots are numbered from 1
        assert slotIndex > 0;
        // This registry requires a definition
        assert definition != null;

        Record newRecord = new Record(name, definition);
        String dsk = duplicateSlotKey(newRecord);
        ArrayList<Integer> existingDuplicateSlotList = m_duplicateSlots.get(dsk);
        if(existingDuplicateSlotList==null) {
            ArrayList<Integer> newDuplicateSlotList = new ArrayList<>();
            newDuplicateSlotList.add(slotIndex);
            m_slotsToRecords.put(slotIndex,newRecord);
            m_duplicateSlots.put(dsk,newDuplicateSlotList);
            m_records.put(slotIndex, newRecord);
        } else {
            existingDuplicateSlotList.add(Integer.valueOf(slotIndex));
        }
    }

    public int firstSlotIndex(Record record) {
        String dsk = duplicateSlotKey(record);
        ArrayList<Integer> duplicateSlotIndexes = m_duplicateSlots.get(dsk);
        assert duplicateSlotIndexes != null;
        assert duplicateSlotIndexes.size() > 0;
        return duplicateSlotIndexes.get(0);
    }

    static String duplicateSlotKey(Record newRecord) {
        String retval = String.format(
            "name='%s' hash=%s",
            newRecord.displayName(), newRecord.audioHash()
        );
        return retval;
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

            // Export raw (compact format) and pretty (more readaable)
            // renderings of the presets as JSON.
            AmpDefinitionExporter ade = new AmpDefinitionExporter(m_outputPath);
            acceptVisitor(ade);

            // For the moment we only want to create suites containing the non-empty
            // default firmware presets at slots 1 to 30.
            AmpBasedPresetSuiteExporter abpse = new AmpBasedPresetSuiteExporter(m_outputPath);
            abpse.setRange(1,30);
            acceptVisitor(abpse);
        }
    }

    /**
     * non-public class Record requires a hash function
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

    public static int outputToFile(String rawTargetPath, String jsonForSuite) {
        try {
            FileOutputStream fos;
            fos = new FileOutputStream(rawTargetPath);
            fos.write(jsonForSuite.getBytes(StandardCharsets.UTF_8));
            return 0;
        } catch (FileNotFoundException e) {
            System.err.println("Unable to open " + rawTargetPath + ", continuing...");
            return -1;
        }
        catch (IOException e) {
            System.err.println("Unable to write to " + rawTargetPath + ", continuing...");
            return -2;
        }
    }

    public Record get(int slotIndex) {
        return m_slotsToRecords.get(slotIndex);
    }

    public static class Record extends PresetRecordBase {
        final String m_definitionRawJson;
        final PresetCanonicalSerializer m_presetCanonicalSerializer;

        public Record(String name, byte[] definitionBytes) {
            super(name);
            m_definitionRawJson = new String(definitionBytes, StandardCharsets.UTF_8);
            m_presetCanonicalSerializer = FenderJsonPresetRegistry.s_gsonCompact.fromJson(
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
            for(
                PresetCanonicalSerializer.PCS_Node node:
                m_presetCanonicalSerializer.audioGraph.nodes
            ) {
                if(node.nodeId.equals("amp")) {
                    return node.FenderId.replace("DUBS_","");
                }
            }
            return null;
        }

        public String audioHash() {
            // We use the GSON @Since annotation to exclude a small number
            // of fields in the DspUnitParameters JSON substructure which
            // have inconsistent serialization according to whether they
            // are original firmware presets, presets edited or imported
            // by FenderTone or via the amp's own controls.
            // Excluding these fields enables us to get the same hash
            // values for presets which are functionally identical but
            // have specific attributes represented differently at the
            // DspUnitParameter level.
            Gson hashingGson = new GsonBuilder().setVersion(0.0).create();

            String cxnsHash = FenderJsonPresetRegistry.stringHash(
                hashingGson.toJson(
                    m_presetCanonicalSerializer.audioGraph.connections
                ),2
            );

            String nodesHash = FenderJsonPresetRegistry.stringHash(
                hashingGson.toJson(
                    m_presetCanonicalSerializer.audioGraph.nodes
                ),4
            );

            return String.format("%s-%s", cxnsHash, nodesHash);
        }

        public String effects() {
            /*
             * The audio graph is represented by two arrays of subobjects:
             * + nodes contains the settings of the DSP units and amplifier
             *   in use; and
             * + connections contains the connection order in which the
             *   graph is traversed.
             * The various pieces of software which edit presets (in
             * the amp firmware, the Fender Tone desktop mobile apps,
             * and in Fender's cloud) do not order these arrays consistently
             * so we need to preprocess the arrays to be able to
             * generate a faithful representation of the effective a
             * audio graph.
             */

            // First we look at the connections
            HashMap<String, ArrayList<String>> cxnMap = new HashMap<>();
            for(
                PresetCanonicalSerializer.PCS_Connection cxn:
                m_presetCanonicalSerializer.audioGraph.connections
            ) {
                ArrayList<String> outputsForInput = cxnMap.get(cxn.input.nodeId);
                if (outputsForInput == null) {
                    // first channel seen for this input
                    outputsForInput = new ArrayList<String>();
                    outputsForInput.add(cxn.output.nodeId);
                    cxnMap.put(cxn.input.nodeId,outputsForInput);
                } else if (outputsForInput.contains(cxn.output.nodeId)) {
                    // second channel for an input/output pair which has already been seen
                } else {
                    // We are seeing a connection list where the index 0 and index 1 records
                    // for the same input connect to different outputs.
                    // This is unexpected and we don't try to handle it.
                    return "*abnormal connection pattern - not analyzed*;";
                }
            }

            // Then we look at the nodes
            HashMap<String, String> nodeMap= new HashMap<>();
            for(
                PresetCanonicalSerializer.PCS_Node node:
                m_presetCanonicalSerializer.audioGraph.nodes
            ) {
                nodeMap.put(node.nodeId,node.FenderId.replace("DUBS_",""));
            }

            // Finally we traverse the connection list, starting and ending at 'preset'
            StringBuilder sb = new StringBuilder();
            String nextNodeType = cxnMap.get("preset").get(0);
            for(int i=0;i<5;++i) {
                boolean insertSeparator=false;
                String nodeName = nodeMap.get(nextNodeType);
                if(!nodeName.equals("Passthru")) {
                    sb.append(
                        nextNodeType.substring(0,1) + ":" + nodeName
                    );
                    insertSeparator = true;
                }
                nextNodeType = cxnMap.get(nextNodeType).get(0);
                if(nextNodeType.equals("preset")) {
                    break;
                } else if(insertSeparator) {
                    final String UNICODE_NON_BREAKING_SPACE = "\u00A0";
                    sb.append(UNICODE_NON_BREAKING_SPACE);
                }
            }
            return sb.toString();
        }
    }
}

class PresetDetailsTableGenerator implements PresetRegistryBase.Visitor {
    private final static String _LINE_FORMAT = "%3d %-16s %-7s %-70s";
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
            "#", "Name", "Hash", "Effect Chain"
        ));
    }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        FenderJsonPresetRegistry.Record fjpr = (FenderJsonPresetRegistry.Record) record;
        assert fjpr != null;
        m_printStream.println(String.format(
            _LINE_FORMAT,
            slotIndex, fjpr.displayName(), fjpr.audioHash(), fjpr.effects()
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
                "The preset with %s is duplicated at the following slots: %s",
                duplicateKey, duplicateSlotList
            ));
        }
    }
}

class AmpDefinitionExporter implements PresetRegistryBase.Visitor {
    final String m_outputPrefix;
    final Gson m_compactGson;
    final Gson m_prettyGson;
    AmpDefinitionExporter(String outputPrefix) {
        m_outputPrefix = outputPrefix;
        m_compactGson = new Gson();
        m_prettyGson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public void visitBeforeRecords(PresetRegistryBase registry) {  }

    @Override
    public void visitRecord(int slotIndex, Object record) {
        FenderJsonPresetRegistry.Record fjpr = (FenderJsonPresetRegistry.Record) record;
        assert fjpr != null;
        String presetBasename = String.format(
            "%s-%s",
            fjpr.displayName().replace(" ","_"),
            fjpr.audioHash()
        );

        // The raw export is the JSON exactly as it was retrieved from the protocol,
        // i.e. compact, with order of dictionary keys preserved.
        String rawJson = fjpr.m_definitionRawJson;
        String rawTargetPath = m_outputPrefix + "/" + presetBasename +".raw_preset.json";
        FenderJsonPresetRegistry.outputToFile(rawTargetPath, rawJson);

        // We also export the GSON compact rendering so that we can compare
        // it to the raw JSON received from the amp.
        String compactJson = m_compactGson.toJson(fjpr.m_presetCanonicalSerializer);
        String compactTargetPath = m_outputPrefix + "/" + presetBasename +".compact.json";
        FenderJsonPresetRegistry.outputToFile(compactTargetPath, compactJson);

        // The pretty export is the GSON pretty rendering of the parsed JSON object
        // i.e. indented, with dictionary keys sorted.
        String prettyJson = m_prettyGson.toJson(fjpr.m_presetCanonicalSerializer);
        String prettyTargetPath = m_outputPrefix + "/" + presetBasename +".pretty_preset.json";
        FenderJsonPresetRegistry.outputToFile(prettyTargetPath, prettyJson);

    }

    @Override
    public void visitAfterRecords(PresetRegistryBase registry)  { }
}

class AmpBasedPresetSuiteExporter implements PresetRegistryBase.Visitor {
    HashMap<String,JsonObject> m_ampPresetSuites;
    final String m_outputPrefix;
    final Gson m_gson;

    AmpBasedPresetSuiteExporter(String outputPrefix) {
        m_outputPrefix = outputPrefix;
        m_gson = new GsonBuilder().setPrettyPrinting().create();
        m_ampPresetSuites = new HashMap<>();
    }

    // The user may want to select a range of presets for export
    // (for example on LT40S to capture only firmware default presets 1-30)
    int m_minSlotIndex = 1;
    int m_maxSlotIndex = 999;
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
        FenderJsonPresetRegistry.Record fjpr = (FenderJsonPresetRegistry.Record) record;
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
        newPreset.addProperty("audioHash", fjpr.audioHash());
        newPreset.addProperty("effects", fjpr.effects());
        presetArray.add(newPreset);
    }

    @Override
    public void visitAfterRecords(PresetRegistryBase registry) {
        for(String presetName: m_ampPresetSuites.keySet()) {
            String targetPath = m_outputPrefix + "/" + presetName + ".amp_presets.json";
            String jsonForSuite = m_gson.toJson(m_ampPresetSuites.get(presetName));
            FenderJsonPresetRegistry.outputToFile(targetPath, jsonForSuite);
        }
    }
}

