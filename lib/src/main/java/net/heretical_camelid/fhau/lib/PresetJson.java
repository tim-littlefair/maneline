package net.heretical_camelid.fhau.lib;

/**
 * This class (and its static inner classes) is constructed to satisfy
 * the recommendations described in
 * https://google.github.io/gson/UserGuide.html#primitives-examples
 * for a class to be suitable for bidirectional serialization to and
 * from JSON using the GSON framework.
 * The order of members exactly matches the order in the JSON
 * retrieved from an LT40S running firmware version 1.0.7 for
 * preset #1 "FENDER  CLEAN   ".
 */
public class PresetJson {
    String nodeType;
    String NodeId;
    String version;
    int numInputs;
    int numOutputs;
    PJ_Info info;
    PJ_AudioGraph audioGraph;
    PresetJson() {}

    static class PJ_AudioGraph {
        PJ_Node[] nodes;
        PJ_Connection[] connections;
    }

    static class PJ_Info {
        String displayName;
        String preset_id;
        String author;
        String source_id;
        int timestamp;
        int created_at;
        String productId;
        boolean is_factory_default = true;
        PJ_Info() { }
    }

    static class PJ_Node {
        String nodeId;
        String dspUnit;
        String FenderId;
        PJ_DspUnitParameters dpsUnitParameters;
        PJ_Node() {}
    }

    static class PJ_DspUnitParameters {
        boolean bypass;
        String bypassType;
        float level;
        int gain;
        String tone;
        PJ_DspUnitParameters() { }
    }

    static class PJ_Connection {
        PJ_Connection_IO input;
        PJ_Connection_IO output;
        PJ_Connection() { }
    }

    static class PJ_Connection_IO {
        int index;
        String nodeId;
        PJ_Connection_IO() {}
    }
}
