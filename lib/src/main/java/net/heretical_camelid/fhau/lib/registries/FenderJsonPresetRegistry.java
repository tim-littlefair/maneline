package net.heretical_camelid.fhau.lib.registries;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.heretical_camelid.fhau.lib.FhauLibException;

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
    static ZipOutputStream s_outputZipStream = null;
    HashMap<Integer,Record> m_slotsToRecords;
    HashMap<String, ArrayList<Integer>> m_duplicateSlots;


    public FenderJsonPresetRegistry(String outputPath) {
        m_outputPath = outputPath;
        if (m_outputPath==null) {
            // all output is to console standard output, no need for filesystem access
        } else if (m_outputPath.toLowerCase().endsWith(".zip")) {
            try {
                s_outputZipStream = new ZipOutputStream(new FileOutputStream(m_outputPath));
            } catch (FileNotFoundException e) {
                System.err.println("Unable to create zip file at " + m_outputPath);
                System.err.println("Check parent directory exists, is writeable, and has capacity");
                throw new FhauLibException(
                    e.getLocalizedMessage(),
                    FhauLibException.FHAU_EXIT_STATUS_LIB_FILE_CREATION_ERROR
                );
            }
        } else {
            // Output is to a directory in the filesystem which
            // may or may not already exist.
            // Make sure the directory exists and has subdirectories
            // for presets and suites
            File outputDir = new File(m_outputPath);
            if(!outputDir.exists()) {
                boolean creationStatus = outputDir.mkdirs();
                if(!creationStatus) {
                    throw new FhauLibException(
                        String.format(
                            "Attempt to create directory at %s failed",
                            outputDir.getAbsolutePath()
                        ),
                        FhauLibException.FHAU_EXIT_STATUS_LIB_DIRECTORY_CREATION_ERROR
                    );
                }
            } else if(!outputDir.isDirectory()) {
                throw new FhauLibException(
                    "Name of directory to be created clashes with preexisting object at " +
                    outputDir.getAbsolutePath(),
                    FhauLibException.FHAU_EXIT_STATUS_LIB_DIRECTORY_CREATION_ERROR
                );
            } else {
                // Target directory already exists - we assume this is intended
            }
            for(String subdir: new String[] { "/presets", "/suites" }) {
                File subdirFile = new File(outputDir + subdir);
                if(!subdirFile.exists()) {
                    boolean creationStatus = subdirFile.mkdirs();
                    if(!creationStatus) {
                        throw new FhauLibException(
                            String.format(
                                "Attempt to create directory at %s failed",
                                subdirFile.getAbsolutePath()
                            ),
                            FhauLibException.FHAU_EXIT_STATUS_LIB_DIRECTORY_CREATION_ERROR
                        );
                    }
                } else if(!subdirFile.isDirectory()) {
                    throw new FhauLibException(
                        "Name of directory to be created clashes with preexisting object at " +
                        subdirFile.getAbsolutePath(),
                        FhauLibException.FHAU_EXIT_STATUS_LIB_DIRECTORY_CREATION_ERROR
                    );
                }
            }
        }
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

    // We start with a list of all slots containing
    // unique presets (including the lowest index copy
    // of presets which are identified as duplicated).
    // We remove each slot index from the list as it is
    // explicitly included in a curated suite.
    // This allows us to create the 'Everything Else'
    // suite to include items which are not included elsewhere.
    private List<Integer> m_slotsNotExportedYet = null;

    // the filenames for suites contain a leading running number
    // so that the dictionary order of filenames can be used
    // to display suites on the UI in the order they are
    // created in the code
    private int m_suiteNumber=0;

    private SlotBasedPresetSuiteExporter createSuite(
        String outputPrefix, String suiteName, Integer... desiredSlotIndexes
    ) {
        if(desiredSlotIndexes.length==0) {
            return null;
        }
        List<Integer> dsiList = new ArrayList<Integer>(List.of(desiredSlotIndexes));
        if(m_slotsNotExportedYet!=null) {
            m_slotsNotExportedYet.removeAll(dsiList);
        }
        m_suiteNumber++;
        return new SlotBasedPresetSuiteExporter(
            outputPrefix,
            String.format("%02d-%s", m_suiteNumber, suiteName),
            dsiList.stream().toArray(Integer[]::new)
        );
    }

    public void dump(PresetSuiteRegistry presetSuiteRegistry) {
        if(m_outputPath == null) {
            generatePresetDetails(System.out);
        } else {
            generatePresetDetails(System.out);

            String outputPathBase;
            if(s_outputZipStream!=null) {
                outputPathBase = "/";
            } else {
                outputPathBase = m_outputPath + "/";
            }

            // Export raw (compact format) and pretty (more readaable)
            // renderings of the presets as JSON.
            String presetPathPrefix = outputPathBase + "presets";
            AmpDefinitionExporter ade = new AmpDefinitionExporter(presetPathPrefix);

            // Export suite records
            String suitePathPrefix =  outputPathBase + "suites";
            m_slotsNotExportedYet = new ArrayList<Integer>();
            m_slotsNotExportedYet.addAll(m_slotsToRecords.keySet());
            m_suiteNumber = 0;
            List<SlotBasedPresetSuiteExporter> suiteExporters =
                Arrays.asList(new SlotBasedPresetSuiteExporter[]{
                    createSuite(
                        // TL: really this is just my favourites
                        suitePathPrefix, "General",
                        31, 1, 30, 2, 7, 8, 15, 29
                    ),
                    createSuite(suitePathPrefix, "Folk", 6, 30),
                    createSuite(suitePathPrefix, "Jazz", 9, 12, 13),
                    createSuite(suitePathPrefix, "Blues", 3, 26, 1, 2, 3),
                    createSuite(suitePathPrefix, "Rock", 4, 8, 10, 17, 18, 19, 22, 27),
                    createSuite(suitePathPrefix, "Heavy", 11, 14, 16, 20, 23, 24, 28),
                    createSuite(suitePathPrefix, "Trippy", 5, 18, 29),
                    createSuite(
                        suitePathPrefix, "Everything Else",
                        m_slotsNotExportedYet.stream().toArray(Integer[]::new)
                    )
                }
            );
            acceptVisitor(ade);
            for(SlotBasedPresetSuiteExporter suiteExporter: suiteExporters) {
                if(suiteExporter!=null) {
                    acceptVisitor(suiteExporter);
                }
            }

            if(presetSuiteRegistry!=null) {
                System.out.println();
                presetSuiteRegistry.dump();
                System.out.println();
            }

            if(s_outputZipStream!=null) {
                try {
                    s_outputZipStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                System.out.println(
                    "Preset and suite data from this run has been saved to file " + m_outputPath
                );
            } else {
                System.out.println(
                    "Preset and suite data from this run has been saved to directory " + m_outputPath
                );
            }
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
            if(s_outputZipStream!=null) {
                ZipEntry e = new ZipEntry(rawTargetPath);
                s_outputZipStream.putNextEntry(e);
                s_outputZipStream.write(jsonForSuite.getBytes(StandardCharsets.UTF_8));
                s_outputZipStream.closeEntry();
            } else {
                FileOutputStream fos;
                fos = new FileOutputStream(rawTargetPath);
                fos.write(jsonForSuite.getBytes(StandardCharsets.UTF_8));
            }
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
            Gson hashingGson1 = new GsonBuilder().setVersion(90.0).create();
            Gson hashingGson2 = new GsonBuilder().setVersion(92.0).create();

            /*
             * As far as we are aware at the moment, there is only one
             * variant of the connections array present in raw JSON
             * sent by the LT40S device => for now, this is not
             * a useful discriminant for the hash.
            String cxnsHash = FenderJsonPresetRegistry.stringHash(
                hashingGson.toJson(
                    m_presetCanonicalSerializer.audioGraph.connections
                ),2
            );
             */

            String nodesHash1 = FenderJsonPresetRegistry.stringHash(
                hashingGson1.toJson(
                    m_presetCanonicalSerializer.audioGraph.nodes
                ),4
            );

            String nodesHash2 = FenderJsonPresetRegistry.stringHash(
                hashingGson2.toJson(
                    m_presetCanonicalSerializer.audioGraph.nodes
                ),4
            );

            return String.format("%s-%s", nodesHash1, nodesHash2);
        }

        /**
         * This function generates a string summarizing the
         * DSP unit types of nodes in the audio chain.
         * This string can help to recognize similarities between presets
         * which differ only in parameters, or which share most DSP units.
         * @return string listing types of non-passthru units in the chain
         */
        public String effects() {
            StringBuilder sb = new StringBuilder();
            boolean insertSeparator=false;
            for(
                PresetCanonicalSerializer.PCS_Node node:
                m_presetCanonicalSerializer.audioGraph.nodes
            ) {
                String nextNodeType = node.nodeId;
                String nodeName = node.FenderId.replace("DUBS_","");
                if(!nodeName.equals("Passthru")) {
                    if(insertSeparator) {
                        final String UNICODE_NON_BREAKING_SPACE = "\u00A0";
                        sb.append(UNICODE_NON_BREAKING_SPACE);
                    }
                    sb.append(
                        nextNodeType.substring(0,1) + ":" + nodeName
                    );
                    insertSeparator = true;
                }

            }
            return sb.toString();
        }

        public String shortInfo() {
            StringBuilder sb = new StringBuilder();
            if(m_presetCanonicalSerializer.info.author.isEmpty()) {
                sb.append("no author, ");
            } else {
                sb.append("author:"+m_presetCanonicalSerializer.info.author+", ");
            }
            if(m_presetCanonicalSerializer.info.source_id.isEmpty()) {
                sb.append("no source_id, ");
            } else {
                sb.append("source_id:"+m_presetCanonicalSerializer.info.source_id+", ");
            }
            sb.append("product_id:"+m_presetCanonicalSerializer.info.product_id+", ");
            sb.append("is_factory_default:"+m_presetCanonicalSerializer.info.is_factory_default);

            return sb.toString();
        }
    }

    public static class PresetDetailsTableGenerator implements Visitor {
        private final static String _LINE_FORMAT = "%3d %-16s %-9s %-70s";
        PrintStream m_printStream;

        public PresetDetailsTableGenerator(PrintStream printStream) {
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
            Record fjpr = (Record) record;
            assert fjpr != null;
            m_printStream.println(String.format(
                _LINE_FORMAT,
                slotIndex, fjpr.displayName(), fjpr.audioHash(), fjpr.effects()
            ));
        }

        @Override
        public void visitAfterRecords(PresetRegistryBase registry) {
            FenderJsonPresetRegistry fjpRegistry = (FenderJsonPresetRegistry) registry;
            assert fjpRegistry != null;
            m_printStream.println();
            m_printStream.println("Duplicated Presets");
            for (String duplicateKey : fjpRegistry.m_duplicateSlots.keySet()) {
                ArrayList<Integer> duplicateSlotList = fjpRegistry.m_duplicateSlots.get(duplicateKey);
                if (duplicateSlotList.size() == 1) {
                    continue;
                }
                m_printStream.println(String.format(
                    "The preset with %s is duplicated at the following slots: %s",
                    duplicateKey, duplicateSlotList
                ));
            }
        }
    }
}

