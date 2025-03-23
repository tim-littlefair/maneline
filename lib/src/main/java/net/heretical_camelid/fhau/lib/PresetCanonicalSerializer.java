package net.heretical_camelid.fhau.lib;

import com.google.gson.Gson;

import java.util.SortedSet;

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
    static Gson s_gson = new Gson();
    String nodeType;
    String NodeId;
    String version;
    int numInputs;
    int numOutputs;
    PCS_Info info;
    PCS_AudioGraph audioGraph;
    PresetCanonicalSerializer() {}

    static class PCS_Comparable implements Comparable<PCS_Comparable> {
        // Static inner classes PCS_Node and PCS_Connection are contained
        // within the JSON format in JSON array objects in which the order
        // of records is neither significant nor consistent between presets
        // which have been created via different pathways.
        // In order to be able to create consistent hashes over these
        // collections we choose to peer them into instances of SortedSet,
        // so the collected items need to implement the Comparable<> interface.
        @Override
        public int compareTo(PCS_Comparable o) {
            String compactJsonThis = s_gson.toJson(this);
            String compactJsonOther = s_gson.toJson(o);
            return compactJsonThis.compareTo(compactJsonOther);
        }
    }

    static class PCS_AudioGraph {
        SortedSet<PCS_Node> nodes;
        SortedSet<PCS_Connection> connections;
        PCS_AudioGraph() {}
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

    static class PCS_Node extends PCS_Comparable {
        String nodeId;
        String dspUnit;
        String FenderId;
        PCS_DspUnitParameters dspUnitParameters;
        PCS_Node() {}
    }

    static class PCS_DspUnitParameters {
        boolean bypass;
        String bypassType;
        float level;
        float gain;
        String tone;
        PCS_DspUnitParameters() { }
    }

    static class PCS_Connection extends PCS_Comparable {
        PCS_Connection_IO input;
        PCS_Connection_IO output;
        PCS_Connection() { }
    }

    static class PCS_Connection_IO {
        int index;
        String nodeId;
        PCS_Connection_IO() {}
    }
}
