package net.heretical_camelid.fhau.lib;

import java.util.regex.Pattern;

/**
 * TODO:
 * Implementations of this abstract base class will be defined at
 * some time in the future so that logic specific to either
 * LT40S or MMP can be kept outside the publicly visible class.
 * For now there are no implementations and the only simulation
 * mode offered is the constant-returning stateless one.
 */
abstract class DeviceDelegateBase {
    public String m_deviceDescription;
    public String m_firmwareVersion;
    public PresetInfo m_presetInfo;
    abstract void startup();
}

class DeviceDelegateLT40S extends DeviceDelegateBase {
    public DeviceDelegateLT40S() {
        m_deviceDescription = "Simulated LT40S";
        m_firmwareVersion = "99.11.13";
        m_presetInfo = PresetInfo.piMixedBag();
    }
    public void startup() {

    }
}
public class SimulatorAmpProvider implements IAmpProvider {
    private final ILoggingAgent m_loggingAgent;
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
    final private DeviceDelegateBase m_delegate;


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

    public SimulatorAmpProvider(
        ILoggingAgent loggingAgent,
        SimulationMode requiredMode
    ) {
        if(loggingAgent!=null) {
            m_loggingAgent = loggingAgent;
        } else {
            m_loggingAgent = new DefaultLoggingAgent();
        }
        PresetInfo pi = new PresetInfo();
        switch(requiredMode)
        {
            case NO_DEVICE:
                m_delegate = null;
                m_presetInfo = new PresetInfo();
                m_deviceDescription = "SimulatedAmplifier SN 123456";
                m_firmwareVersion = "99.00.13";
                m_presetInfo = PresetInfo.piMixedBag();
                break;

            case LT40S:
                m_delegate = new DeviceDelegateLT40S();
                break;

            case MMP:
            default:
                throw new UnsupportedOperationException(
                    "The only simulation mode supported at present is SimulationMode.NO_DEVICE"
                );
        }
        if(m_delegate != null) {
            m_deviceDescription = m_delegate.m_deviceDescription;
            m_firmwareVersion = m_delegate.m_firmwareVersion;
            m_presetInfo = m_delegate.m_presetInfo;
        }
    }
    @Override
    public boolean connect() {
        m_loggingAgent.appendToLog(0,String.format(
            "Connected to device '%s', firmware version '%s'",
            m_deviceDescription, m_firmwareVersion
        ));
        return true;
    }

    @Override
    public void sendCommand(String commandHexString) { }

    @Override
    public void expectReports(Pattern[] reportHexStringPatterns) {  }

    @Override
    public PresetInfo getPresetInfo(PresetInfo requestedPresets) {
        assert requestedPresets == null;
        return m_presetInfo;
    }

    public static void main(String[] args) {
        SimulatorAmpProvider sap = new SimulatorAmpProvider(null, SimulationMode.NO_DEVICE);
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
