package net.heretical_camelid.fhau.lib;

import java.util.regex.Pattern;

/**
 * TODO:
 * Implementations of this interface will be defined at
 * some time in the future so that logic specific to either
 * LT40S or MMP can be kept outside the publicly visible class.
 * For now there are no implementations and the only simulation
 * mode offered is the constant-returning stateless one.
 */
interface IDeviceDelegate {
    void startup();
}
public class SimulatorAmpProvider implements IAmpProvider {
    PresetInfo m_presetInfo;

    String m_deviceDescription = null;

    String m_firmwareVersion = null;

    interface IVisitor {
        void setAmpState(
            String deviceDescription,
            String firmwareVersion,
            PresetInfo presetInfo
        );
    }
    public void acceptVisitor(IVisitor visitor) {
        visitor.setAmpState(m_deviceDescription, m_firmwareVersion, m_presetInfo);
    }
    final private IDeviceDelegate m_delegate;


    /**
     * The simulator can be instantiated in one of the following modes
      */
    public enum SimulationMode {
        /**
         * Simulator is stateless and returns constant values
         * Commands can be sent to it but they will not affect
         * its state.
         */
        NO_DEVICE,
        /**
         * Simulator emulates the protocol of the Mustang LT40S combo
         * amp, commands sent will be parsed using LT40S protocol
         * and can change its state.
         */
        LT40S,
        /**
         * Simulator emulates the protocol of the Mustang Micro Plus headphone
         * amp, commands sent will be parsed using MMP protocol
         * and can change its state.
         */
        MMP
    }

    public SimulatorAmpProvider(SimulationMode requiredMode) {
        PresetInfo pi = new PresetInfo();
        switch(requiredMode)
        {
            case NO_DEVICE:
                m_delegate = null;
                m_presetInfo = new PresetInfo();
                m_deviceDescription = "SimulatedAmplifier SN 123456";
                m_firmwareVersion = "99.00.13";

                PresetRecord pr1 = new PresetRecord("COOL SOUND",1);
                pr1.m_state = PresetRecord.PresetState.ACCEPTED;
                m_presetInfo.add(pr1);

                PresetRecord pr2 = new PresetRecord("WARM SOUND",2);
                pr2.m_state = PresetRecord.PresetState.ACCEPTED;
                m_presetInfo.add(pr2);

                // We want to support sparse instances of PresetInfo, so
                // this one is an example
                PresetRecord pr5 = new PresetRecord("SPARSE SOUND",5);
                pr5.m_state = PresetRecord.PresetState.ACCEPTED;
                m_presetInfo.add(pr5);

                break;

            case LT40S:
            case MMP:
            default:
                throw new UnsupportedOperationException(
                    "The only simulation mode supported at present is SimulationMode.NO_DEVICE"
                );
        }
    }
    @Override
    public boolean connect(StringBuilder sb) {
        sb.append("Simulated device connected\n");
        return true;
    }

    @Override
    public void sendCommand(String commandHexString, StringBuilder sb) { }

    @Override
    public void expectReports(Pattern[] reportHexStringPatterns, StringBuilder sb) {  }

    @Override
    public PresetInfo getPresetInfo(PresetInfo requestedPresets) {
        assert requestedPresets == null;
        return m_presetInfo;
    }

    public static void main(String[] args) {
        SimulatorAmpProvider sap = new SimulatorAmpProvider(SimulationMode.NO_DEVICE);
        SimulatorAmpProvider.IVisitor testVisitor = new IVisitor() {

            @Override
            public void setAmpState(String deviceDescription, String firmwareVersion, PresetInfo presetInfo) {
                System.out.println("deviceDescription: " + deviceDescription);
                System.out.println("firmwareVersion: " + firmwareVersion);
                System.out.println("Presets:");
                PresetInfo.IVisitor presetVisitor = new PresetInfo.IVisitor() {
                    @Override
                    public void visit(PresetRecord pr) {
                        System.out.println(String.format("index:%03d state:%-10s name:%s",
                            pr.m_slotNumber, pr.m_state, pr.m_name
                        ));
                    }
                    @Override
                    public void setActivePresetIndex(int activePresetIndex) {
                        PresetRecord pr = presetInfo.m_presetRecords.get(activePresetIndex);
                        if( pr!=null ) {
                            System.out.println(String.format(
                                "Active preset is '%s' in slot %d",
                                pr.m_name, activePresetIndex
                            ));
                        } else {
                            System.out.println(String.format(
                                "Active preset index is %d but there is no record in that slot",
                                activePresetIndex
                            ));
                        }

                    }
                };
                presetInfo.acceptVisitor(presetVisitor);
            }
        };
        sap.acceptVisitor(testVisitor);

        System.out.println("Tests for SimulatorAmplifierProvider done");
    }
}
