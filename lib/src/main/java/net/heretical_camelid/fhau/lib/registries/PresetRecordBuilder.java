package net.heretical_camelid.fhau.lib.registries;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import com.google.gson.Gson;


public class PresetRecordBuilder {
    PresetCanonicalSerializer m_pcs;
    private static final Class m_dupClass;
    static {
        try {
            m_dupClass = Class.forName(
                "net.heretical_camelid.fhau.lib.registries.PresetCanonicalSerializer$PCS_DspUnitParameters"
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public PresetRecordBuilder() {
        m_pcs = new PresetCanonicalSerializer();
        m_pcs.info = new PresetCanonicalSerializer.PCS_Info();
        m_pcs.audioGraph = new PresetCanonicalSerializer.PCS_AudioGraph();
        m_pcs.audioGraph.nodes = new PresetCanonicalSerializer.PCS_Node[5];
        String[] nodeIdValues = new String[]{
            "stomp", "mod", "amp", "delay", "reverb"
        };
        for (int i = 0; i < m_pcs.audioGraph.nodes.length; ++i) {
            m_pcs.audioGraph.nodes[i] = new PresetCanonicalSerializer.PCS_Node();
            m_pcs.audioGraph.nodes[i].nodeId = nodeIdValues[i];
            m_pcs.audioGraph.nodes[i].FenderId = "DUBS_Passthru";
            m_pcs.audioGraph.nodes[i].dspUnitParameters = new PresetCanonicalSerializer.PCS_DspUnitParameters();
        }
    }

    public PresetRecordBuilder setDisplayName(
        String displayName
    ) {
        m_pcs.info.displayName = displayName;
        return this;
    }

    public PresetRecordBuilder setAmpName(
        String ampName
    ) {
        m_pcs.audioGraph.nodes[2].FenderId = ampName;
        return this;
    }

    public PresetRecordBuilder setEffectName(
            int nodeIndex, String effectName
    ) {
        m_pcs.audioGraph.nodes[nodeIndex].FenderId = effectName;
        return this;
    }

    public PresetRecordBuilder setDspUnitParameter(
        int nodeIndex,  String paramName, Object paramValue
    ) {
        try {
            Field targetField = null;
            for(Field f: m_dupClass.getDeclaredFields()) {
                if(paramName.equals(f.getName())) {
                    targetField = f;
                    break;
                }
            }
            targetField.set(
                m_pcs.audioGraph.nodes[nodeIndex].dspUnitParameters,
                paramValue
            );
            return this;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public PresetRecord build() {
        return new PresetRecord(
            m_pcs.info.displayName,
            new Gson().toJson(m_pcs).getBytes(StandardCharsets.UTF_8)
        );
    }
}
