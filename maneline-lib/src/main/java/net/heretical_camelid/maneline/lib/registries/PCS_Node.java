package net.heretical_camelid.maneline.lib.registries;

import com.google.gson.annotations.Since;

public class PCS_Node implements Comparable<PCS_Node> {
    public String nodeId;
    public String dspUnit;
    public String FenderId;

    // We want to be able to calculate a hash which excludes
    // the dspUnitParameters subtrees so that we can easily
    // spot pairs or sets of presets which use the same
    // DSP unit type but differ in unit parameters only.
    // The @Since(91) annotation allows us to do this
    @Since(91)
    public PresetCanonicalSerializer.PCS_DspUnitParameters dspUnitParameters;

    public PCS_Node() {
    }

    public int compareTo(PCS_Node other) {
        if(other==null) {
            return -1;
        }
        int nodeid_index_this = PresetCanonicalSerializer._NODEID_ORDER.indexOf(this.nodeId);
        int nodeid_index_other = PresetCanonicalSerializer._NODEID_ORDER.indexOf(other.nodeId);
        return Integer.compare(nodeid_index_this, nodeid_index_other);
    }
}
