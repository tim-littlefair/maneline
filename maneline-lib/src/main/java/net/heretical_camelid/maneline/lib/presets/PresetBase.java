package net.heretical_camelid.maneline.lib.presets;

import net.heretical_camelid.maneline.lib.AbstractMessageProtocolBase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public class PresetBase extends JSONObject {
    protected class DspModule extends JSONObject {
        public DspModule(String moduleName, Object moduleType) {
            put("FenderId", moduleName);
            put("nodeId", moduleType);
            TreeMap sortedParamMap = new TreeMap<String, Object>();
            put("dspUnitParameters", new JSONObject(sortedParamMap));
        }
    }



    // convenient debug utility, should always be no-op for checked in code
    static private void _trace(Object msg) {
        // System.out.println(msg);
    }

    // The default constructor of org.json.JSONObject will
    // use an implementation of Map which does not preserve
    // ordering of keys.
    // While this is faithful to the semantics of JSON as
    // defined in RFC 7159, for our application we want to
    // be able to compare objects of the extended class for
    // equivalence, and it is convenient to do this by
    // equality of serialization.
    // By using the constructor which accepts a preexisting
    // Map implementation object (both for the derived class
    // (Java) object itself and for all instances of
    // org.json.JSONObject attached to it, we are able to lock
    // in stable ordering behaviour.
    static private Map<String,Object> createMapImplementation() {
        return new TreeMap<String,Object>();
    }

    protected String m_exportRawExtension = null;
    final private byte[] m_definitionBytes;

    protected PresetBase(byte[] presetBytes, String companionAppName) {
        super();
        m_definitionBytes = presetBytes;

        JSONObject audioGraph = new JSONObject(createMapImplementation());
        audioGraph.put("nodes", new JSONArray(5));
        put("audioGraph", audioGraph);
        put("info", new JSONObject(createMapImplementation()));
        put("_metadata", new JSONObject(createMapImplementation()));

        JSONArray rawPackets = new JSONArray( 1+ ((presetBytes.length-1)/8));
        for(int packetSeq=0; packetSeq<rawPackets.length(); ++packetSeq) {
            rawPackets.put(AbstractMessageProtocolBase.bufferToHex2(
                Arrays.copyOfRange(presetBytes,64*packetSeq,64*(packetSeq+1)),
                ""
            ));
        }
        put(String.format("_%s_packets",companionAppName), rawPackets);
        m_exportRawExtension = String.format(".%s.json",companionAppName);
    }

    protected PresetBase(String presetJson, String companionAppName) {
        super(new JSONObject(presetJson).toMap());
        m_definitionBytes = presetJson.toString().getBytes();
        assert audioGraph() != null;
        // assert audioGraph_nodes() != null;
        assert info() != null;
        assert displayName() != null;
        m_exportRawExtension = String.format(".%s.json",companionAppName);
    }

    public String displayName() {
        return (String) getSubObject(
            this,
            List.of((Object[]) new String[]{"info", "displayName"})
        );
    }

    public byte[] definitionBytes() {
        return m_definitionBytes;
    }

    public static Object getSubObject(Object target, List<Object> keySeq) {
        for(Object k: keySeq) {
            _trace(k);
            if(target instanceof JSONObject) {
                assert k instanceof String;
                target = ((JSONObject)target).get((String)k);
            } else if(target instanceof JSONArray) {
                assert k instanceof Integer;
                target = ((JSONArray)target).get((int) k);
            } else {
                return null;
            }
            _trace(target);
        }
        return target;
    }

    public JSONObject info() {
        return (JSONObject) getSubObject(this, List.of(new Object[] {"info"} ));
    }

    public JSONObject audioGraph() {
        return (JSONObject) getSubObject(this, List.of(new Object[] {"audioGraph"} ));
    }

    public JSONArray audioGraph_nodes() {
        return (JSONArray) getSubObject(this, List.of(new Object[] {"audioGraph", "nodes" } ));
    }

    private JSONArray m_audioGraph_nodes;


    public void addAudioGraphAmp(
        String fenderId, String nodeId, String dspUnitParametersJson
    ) {
        JSONObject node = new JSONObject(createMapImplementation());
        node.put("FenderId", fenderId);
        node.put("nodeId", nodeId);
        node.put("dspUnitParameters", createMapImplementation());
        // TODO handle params
        m_audioGraph_nodes.put(4,node);
    }

    public void addAudioGraphNode(
        String fenderId, String nodeId, String dspUnitParametersJson, int pos
    ) {
        JSONObject node = new JSONObject(createMapImplementation());
        node.put("FenderId", fenderId);
        node.put("nodeId", nodeId);
        node.put("dspUnitParameters", createMapImplementation());
        // TODO handle params
        m_audioGraph_nodes.put(pos<4?pos:pos+1,node);
    }

    /*public PresetRecord exportPresetRecord() {
        return new PresetRecord(
            (String) getSubObject(this, List.of("info", "displayName")),
            this.toString().getBytes(StandardCharsets.UTF_8)
        );
    }*/

    public String exportRawExtension() {
        return m_exportRawExtension;
    }
    public JSONObject metadata() {
        return getJSONObject("_metadata");
    }

    static public void main(String args[]) {
/*
        PresetBase testPreset1 = (PresetBase) (new PresetBase().put("displayName","testPreset1"));
        System.out.println(testPreset1.toString(4));
        JSONArray nodesArray1 = testPreset1.m_audioGraph_nodes;
        assert nodesArray1 != null;
        assert nodesArray1.length()==5;
        for(int i=0; i<nodesArray1.length(); ++i) {
         assert nodesArray1.isNull(i);
        }

        PresetBase testPreset2 = (PresetBase) (new PresetBase().put("displayName","testPreset2"));
        testPreset2.addAudioGraphNode(
         "Deluxe65", "amp",
         "{}",
         2
        );
        System.out.println(testPreset2.toString(4));
        assert testPreset2.m_audioGraph_nodes.length() == 5;
        String ampFenderId = (String) getSubObject(
         testPreset2,
         List.of("audioGraph","nodes", 2, "FenderId")
        );
        assert ampFenderId.equals("Deluxe65");

        PresetBase testPreset3 = create(testPreset2.exportPresetRecord());
        testPreset3.addAudioGraphNode("Passthru","stomp",null,0);
        testPreset3.addAudioGraphNode("SineTremolo","mod",null,1);
        testPreset3.addAudioGraphNode("Passthru","delay",null,3);
        testPreset3.addAudioGraphNode("Passthru","reverb",null,4);
        System.out.println(testPreset3.toString(4));
        // The hash below needs to be maintained manually
        PresetRecord pr3 = testPreset3.exportPresetRecord();
        String expectedHash = "7b19-14e2";
        assert pr3.audioHash().equals(expectedHash): String.format(
         "audioHash mismatch: expected=%s actual=%s",
         expectedHash, pr3.audioHash()
        );
*/
        System.exit(0);
    }

    public void makeCanonical() {
    }
}
