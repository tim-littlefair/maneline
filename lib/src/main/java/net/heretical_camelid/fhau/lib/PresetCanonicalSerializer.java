package net.heretical_camelid.fhau.lib;

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
    String nodeType;
    String NodeId;
    String version;
    int numInputs;
    int numOutputs;
    PCS_Info info;
    PCS_AudioGraph audioGraph;
    PresetCanonicalSerializer() {}

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

    static class PCS_Node {
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

    static class PCS_Connection {
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
