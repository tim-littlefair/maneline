package net.heretical_camelid.fhau.lib;

import com.google.gson.Gson;

import java.util.Arrays;

/**
 * This class and its static inner classes are constructed to satisfy
 * the recommendations described in
 * https://google.github.io/gson/UserGuide.html#primitives-examples
 * for a class to be suitable for bidirectional serialization to and
 * from JSON using the GSON framework.
 * The primary use of this class is to standardize the order of
 * fields when the preset or a subtree of it is serialized for
 * the purpose of comparing preset content or generating a hash.
 * The order of members exactly matches the order in the JSON
 * retrieved from an LT40S running firmware version 1.0.7 for
 * preset #1 "FENDER  CLEAN   ".
 */
public class PresetCanonicalSerializer {
    static Gson s_gsonCompact = new Gson();

    String nodeType;
    String NodeId;
    String version;
    int numInputs;
    int numOutputs;
    PCS_Info info;
    PCS_AudioGraph audioGraph;
    public PresetCanonicalSerializer() {}
    public void makeCanonical() {
        // The two arrays have inconsistent order when the same preset
        // is copied to a different slot (or maybe when it is edited
        // in Fender Tone, even if the final state is equal to the
        // original state).
        // Sorting the arrays resolves the inconsistency and helps to
        // reduce the number of presets with the same name and
        // identical parameters but different hashes due to JSON
        // element ordering
        Arrays.sort(audioGraph.connections);
        Arrays.sort(audioGraph.nodes);
    }

    static class PCS_AudioGraph {
        PCS_Node[] nodes;
        PCS_Connection[] connections;
    }

    static class PCS_Info {
        String displayName;
        String preset_id;
        String author;
        String source_id;
        int timestamp;
        int created_at;
        String productId;
        boolean is_factory_default = true;
        int bpm;
        PCS_Info() { }
    }

    static class PCS_Node implements Comparable<PCS_Node> {
        String nodeId;
        String dspUnit;
        String FenderId;
        PCS_DspUnitParameters dspUnitParameters;
        PCS_Node() {}
        public int compareTo(PCS_Node other) {
            return s_gsonCompact.toJson(this).compareTo(s_gsonCompact.toJson(other));
        }
    }

    static class PCS_DspUnitParameters {
        boolean bypass;
        // TODO:
        // bypassType appears to be optional and finishes up with inconsistent
        // values when a preset is copied or imported via FenderTone as
        // opposed to the original firmware presets.
        // Investigate if the inconsistent values actually affect preset sound.
        // If not maybe skip serializing this?
        // String bypassType;
        float level;
        float gain;
        float tone;
        PCS_DspUnitParameters() { }
    }

    static class PCS_Connection implements Comparable<PCS_Connection> {
        PCS_Connection_IO input;
        PCS_Connection_IO output;
        PCS_Connection() { }
        public int compareTo(PCS_Connection other) {
            return s_gsonCompact.toJson(this).compareTo(s_gsonCompact.toJson(other));
        }
    }

    static class PCS_Connection_IO {
        String nodeId;
        int index;
        PCS_Connection_IO() {}
    }
}
