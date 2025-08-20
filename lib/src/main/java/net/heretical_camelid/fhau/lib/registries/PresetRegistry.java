package net.heretical_camelid.fhau.lib.registries;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.heretical_camelid.fhau.lib.FhauLibException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PresetRegistry {
    final static Gson s_gsonCompact = new Gson();
    final static Gson s_gsonPretty = new GsonBuilder().setPrettyPrinting().create();
    static ZipOutputStream s_outputZipStream = null;
    final String m_outputPath;
    HashMap<Integer, PresetRecord> m_records;
    HashMap<Integer, PresetRecord> m_slotsToRecords;
    HashMap<String, Integer> m_audioHashesToSlots;
    HashMap<String, ArrayList<Integer>> m_duplicateSlots;

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

    public PresetRegistry(String outputPath) {
        m_records = new HashMap<>();
        m_outputPath = outputPath;
        m_slotsToRecords = new HashMap<>();
        m_audioHashesToSlots = new HashMap<>();
        m_duplicateSlots = new HashMap<>();

        initialiseOutput();
    }

    static String duplicateSlotKey(PresetRecord newRecord) {
        String retval = String.format(
            "name='%s' hash=%s",
            newRecord.displayName(), newRecord.audioHash()
        );
        return retval;
    }

    /**
     * non-public class PresetRecord requires a hash function
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
        String retval;
        String method;
        try {
            method = "SHA-256";
            MessageDigest md = MessageDigest.getInstance(method);
            md.update(inputString.getBytes(UTF_8));
            String mdHexString = new BigInteger(md.digest()).toString(16);
            retval = mdHexString.substring(mdHexString.length()-prefixLength, mdHexString.length());
        } catch (NoSuchAlgorithmException e) {
            method = "Object.hashCode()";
            retval = Integer.toHexString(inputString.hashCode()).substring(0, prefixLength);
        }
        /*
        System.out.println(String.format(
            "Hashing %d bytes using %s: %s", inputString.length(), method, inputString
        ));
         */
        return retval;
    }

    public static int outputToFile(String rawTargetPath, String content) {
        try {
            if(s_outputZipStream !=null) {
                ZipEntry e = new ZipEntry(rawTargetPath);
                s_outputZipStream.putNextEntry(e);
                s_outputZipStream.write(content.getBytes(UTF_8));
                s_outputZipStream.closeEntry();
            } else {
                FileOutputStream fos;
                fos = new FileOutputStream(rawTargetPath);
                fos.write(content.getBytes(UTF_8));
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

    public void acceptVisitor(Visitor visitor) {
        visitor.visitBeforeRecords(this);
        for (int i: m_records.keySet()) {
            Object record = m_records.get(i);
            if (record != null) {
                visitor.visitRecord(i, record);
            }
        }
        visitor.visitAfterRecords(this);
    }

    public void generateNameTable(PrintStream ostream) {
        this.acceptVisitor(new PresetNameTableGenerator(ostream));
    }

    public void dump() {
        generateNameTable(System.out);
    }

    public int uniquePresetCount() {
        return m_records.size();
    }

    public void register(int slotIndex, String name, byte[] definition) {
        // Slots are numbered from 1
        assert slotIndex > 0;
        // This registry requires a definition
        assert definition != null;

        PresetRecord newRecord = new PresetRecord(name, definition);
        String dsk = PresetRegistry.duplicateSlotKey(newRecord);
        ArrayList<Integer> existingDuplicateSlotList = m_duplicateSlots.get(dsk);
        if(existingDuplicateSlotList==null) {
            ArrayList<Integer> newDuplicateSlotList = new ArrayList<>();
            newDuplicateSlotList.add(slotIndex);
            m_slotsToRecords.put(slotIndex,newRecord);
            m_audioHashesToSlots.put(newRecord.audioHash(), slotIndex);
            m_duplicateSlots.put(dsk,newDuplicateSlotList);
            m_records.put(slotIndex, newRecord);
        } else {
            existingDuplicateSlotList.add(Integer.valueOf(slotIndex));
        }
    }

    public int firstSlotIndex(PresetRecord record) {
        String dsk = PresetRegistry.duplicateSlotKey(record);
        ArrayList<Integer> duplicateSlotIndexes = m_duplicateSlots.get(dsk);
        assert duplicateSlotIndexes != null;
        assert duplicateSlotIndexes.size() > 0;
        return duplicateSlotIndexes.get(0);
    }

    public void generatePresetDetails(PrintStream printStream) {
        acceptVisitor(new PresetDetailsTableGenerator(printStream));
    }

    private SlotBasedPresetSuiteExporter createSuite(
        String outputPrefix, String suiteName, Integer... desiredSlotIndexes
    ) {
        if(desiredSlotIndexes.length==0) {
            return null;
        }
        List<Integer> dsiList = new ArrayList<Integer>(List.of(desiredSlotIndexes));
        m_suiteNumber++;
        if(m_slotsNotExportedYet!=null) {
            m_slotsNotExportedYet.removeAll(dsiList);
        }
        return new SlotBasedPresetSuiteExporter(
            outputPrefix,
            String.format("%02d-%s", m_suiteNumber, suiteName),
            dsiList.stream().toArray(Integer[]::new)
        );
    }

    public void dump(SuiteRegistry suiteRegistry) {
        generatePresetDetails(System.out);
        if(m_outputPath != null) {
            String outputPathBase;
            if(s_outputZipStream !=null) {
                outputPathBase = "/";
            } else {
                outputPathBase = m_outputPath + "/";
            }

            // Export raw (compact format) and pretty (more readaable)
            // renderings of the presets as JSON.
            String presetPathPrefix = outputPathBase + "presets";
            AmpDefinitionExporter ade = new AmpDefinitionExporter(presetPathPrefix);
            acceptVisitor(ade);

            // Export a CSV listing the modules for each preset
            acceptVisitor(
                new PresetCsvGenerator(outputPathBase + "presets.csv")
            );

            // Export suite records
            String suitePathPrefix =  outputPathBase + "suites";
            m_suiteNumber = 0;
            List<SlotBasedPresetSuiteExporter> suiteExporters =
                Arrays.asList(new SlotBasedPresetSuiteExporter[]{
                    createSuite(
                        // TL: really this is just my favourites
                        suitePathPrefix, "PickNMix",
                        1, 2, 7, 8, 14, 15, 29, 30, 31
                    ),
                    createSuite(suitePathPrefix, "Folk", 1, 10, 30),
                    createSuite(suitePathPrefix, "Blues", 3, 8, 17, 26),
                    createSuite(suitePathPrefix, "Rock", 4, 19, 20, 22),
                    createSuite(suitePathPrefix, "Jazz", 9, 12, 13),
                    createSuite(suitePathPrefix, "Country", 5, 6, 23, 25, 27),
                    createSuite(suitePathPrefix, "Heavy", 2, 7, 11, 14, 16, 28),
                    createSuite(suitePathPrefix, "Trippy", 15, 18, 21, 24, 29),

                    // We also create a JSON file in the suite format 
                    // containing all presets.
                    // Note that SBPSE interprets an empty list of 
                    // presets as "include all".
                    new SlotBasedPresetSuiteExporter(
                        outputPathBase, "all-presets"
                    )
                }
            );
            for(SlotBasedPresetSuiteExporter suiteExporter: suiteExporters) {
                if(suiteExporter!=null) {
                    acceptVisitor(suiteExporter);
                    suiteRegistry.m_suites.add(new SuiteRecord(
                        suiteExporter.m_suiteName, suiteExporter.m_presetRecords
                    ));
                }
            }

            // We also create a JSON file containing presets 1-30
            System.out.println();
            suiteRegistry.dump();
            System.out.println();

            if(s_outputZipStream !=null) {
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

    public PresetRecord get(int slotIndex) {
        return m_slotsToRecords.get(slotIndex);
    }

    public Integer findAudioHash(String audioHash, String presetName) {
        Integer retval = m_audioHashesToSlots.get(audioHash);
        if(retval==null) {
            for (int i : m_slotsToRecords.keySet()) {
                PresetRecord r = m_slotsToRecords.get(i);
                if (r.displayName().equals(presetName)) {
                    System.out.println(String.format(
                        "Name matches but hash doesn't (%s:%s/%s:%s)",
                        presetName, audioHash, r.displayName(), r.audioHash()
                    ));
                    retval = i;
                    break;
                }
            }
            if(retval==null) {
                System.out.println(String.format(
                    "No match found for audioHash %s (presetName %s)",
                    audioHash, presetName
                ));
            }
        }
        return retval;
    }

    protected void initialiseOutput() {
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
    }

    public Set<Integer> getUniquePresetIndices() {
        return m_records.keySet();
    }

    public static interface Visitor {
        void visitBeforeRecords(PresetRegistry registry);
        void visitRecord(int slotIndex, Object record);
        void visitAfterRecords(PresetRegistry registry);
    }

    public static class PresetDetailsTableGenerator implements Visitor {
        private final static String _LINE_FORMAT = "%3d %-16s %-9s %-70s";
        PrintStream m_printStream;

        public PresetDetailsTableGenerator(PrintStream printStream) {
            m_printStream = printStream;
        }

        @Override
        public void visitBeforeRecords(PresetRegistry registry) {
            m_printStream.println();
            m_printStream.println("Unique Presets");
            m_printStream.println(String.format(
                _LINE_FORMAT.replace("%3d", "%3s"),
                "#", "Name", "Hash", "Effect Chain"
            ));
        }

        @Override
        public void visitRecord(int slotIndex, Object record) {
            PresetRecord fjpr = (PresetRecord) record;
            assert fjpr != null;
            m_printStream.println(String.format(
                _LINE_FORMAT,
                slotIndex, fjpr.displayName(), fjpr.audioHash(),
                fjpr.effects(PresetRecord.EffectsLevelOfDetails.MODULES_ONLY)
            ));
        }

        @Override
        public void visitAfterRecords(PresetRegistry registry) {
            PresetRegistry fjpRegistry = registry;
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

