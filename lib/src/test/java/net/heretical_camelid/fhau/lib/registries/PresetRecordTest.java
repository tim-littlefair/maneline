package net.heretical_camelid.fhau.lib.registries;

import com.google.gson.Gson;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class PresetRecordTest {

    @org.junit.Before
    public void setUp() throws Exception {
    }

    @org.junit.After
    public void tearDown() throws Exception {
    }
    @Test
    public void displayName() {
        PresetRecord dnTestPR = PresetRecordBuilder.presetRecord_EMPTY();
        System.out.println(String.format(
            "Preset created with name %s",dnTestPR.displayName()
        ));
        assert(PresetRecordBuilder.PRESET_NAME_EMPTY.equals(dnTestPR.displayName()));
    }

    @Test
    public void ampName() {
        PresetRecord anTestPR = PresetRecordBuilder.presetRecord_FENDER_CLEAN();
        System.out.println(String.format(
            "Preset created with name %s using amplifier %s",
            anTestPR.displayName(), anTestPR.ampName()
        ));
        assert(PresetRecordBuilder.AMP_NAME_TWIN65.equals(
            "DUBS_" + anTestPR.ampName()
        ));
    }

    @Test
    public void audioHash() {
        PresetRecord dnTestPR = PresetRecordBuilder.presetRecord_EMPTY();
        PresetRecord anTestPR = PresetRecordBuilder.presetRecord_FENDER_CLEAN();

    }

    @Test
    public void effects() {
    }

    @Test
    public void shortInfo() {
    }
}

class PresetRecordBuilder {
    PresetCanonicalSerializer m_pcs;
    PresetRecordBuilder() {
        m_pcs = new PresetCanonicalSerializer();
        m_pcs.info = new PresetCanonicalSerializer.PCS_Info();
        m_pcs.audioGraph = new PresetCanonicalSerializer.PCS_AudioGraph();
        m_pcs.audioGraph.nodes = new PresetCanonicalSerializer.PCS_Node[5];
        String[] nodeIdValues = new String[] {
            "stomp", "mod", "amp", "delay", "reverb"
        };
        for(int i=0; i<m_pcs.audioGraph.nodes.length; ++i) {
            m_pcs.audioGraph.nodes[i] = new PresetCanonicalSerializer.PCS_Node();
            m_pcs.audioGraph.nodes[i].nodeId = nodeIdValues[i];
            m_pcs.audioGraph.nodes[i].FenderId = "DUBS_Passthru";
            m_pcs.audioGraph.nodes[i].dspUnitParameters = new PresetCanonicalSerializer.PCS_DspUnitParameters();
        }
    }

    final static String PRESET_NAME_EMPTY = "EMPTY           ";
    static PresetRecord presetRecord_EMPTY() {
        return new PresetRecordBuilder()
            .setDisplayName(PRESET_NAME_EMPTY)
            .setAmpNameAndVolume(
                "DUBS_LinearGain",
                0.0
            )
            .build();
    }

    final static String PRESET_NAME_FENDER_CLEAN = "FENDER  CLEAN   ";
    final static String AMP_NAME_TWIN65 = "DUBS_Twin65";
    private static final String DELAY_EFFECT_NAME_SPRING65 = "DUBS_Spring65";
    static PresetRecord presetRecord_FENDER_CLEAN() {
        return new PresetRecordBuilder()
            .setDisplayName(PRESET_NAME_FENDER_CLEAN)
            .setEffectNameAndParams(
                0,"DUBS_SimpleCompressor",
                0.5555556, 0.5, 0.342816
            )
            .setAmpNameAndVolume(
                AMP_NAME_TWIN65,
                0.0
            )
            .setEffectNameAndParams(
                0,DELAY_EFFECT_NAME_SPRING65,
                0.5, 0.5, 0.5
            )
            .build();
    }

    PresetRecordBuilder setDisplayName(
        String displayName
    ) {
        m_pcs.info.displayName = displayName;
        return this;
    }
    PresetRecordBuilder setAmpNameAndVolume(
        String ampName,
        double ampVolume
    ) {
        m_pcs.audioGraph.nodes[2].FenderId = ampName;
        m_pcs.audioGraph.nodes[2].dspUnitParameters.volume = ampVolume;
        return this;
    }

    PresetRecordBuilder setEffectNameAndParams(
        int nodeIndex,
        String effectName,
        double bass, double mid, double treb
    ) {
        m_pcs.audioGraph.nodes[nodeIndex].FenderId = effectName;
        m_pcs.audioGraph.nodes[nodeIndex].dspUnitParameters.bass = bass;
        m_pcs.audioGraph.nodes[nodeIndex].dspUnitParameters.mid = mid;
        m_pcs.audioGraph.nodes[nodeIndex].dspUnitParameters.treb = treb;
        return this;
    }

    PresetRecord build() {
        return new PresetRecord(
            m_pcs.info.displayName,
            new Gson().toJson(m_pcs).getBytes(StandardCharsets.UTF_8)
        );
    }
}