package net.heretical_camelid.fhau.lib.delegates;

import net.heretical_camelid.fhau.lib.DefaultLoggingAgent;
import net.heretical_camelid.fhau.lib.PresetInfo;
import net.heretical_camelid.fhau.lib.PresetRecord;
import net.heretical_camelid.fhau.lib.interfaces.IAmpProvider;
import net.heretical_camelid.fhau.lib.interfaces.ILoggingAgent;
import net.heretical_camelid.fhau.lib.registries.SuiteRecord;
import net.heretical_camelid.fhau.lib.registries.SuiteRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class SimulatorAmpProvider implements IAmpProvider {
    private final ILoggingAgent m_loggingAgent;
    PresetInfo m_presetInfo;

    String m_deviceDescription = null;

    String m_firmwareVersion = null;

    interface IVisitor {
        static PresetInfo piMixedBag() {
            PresetInfo piMixedBag = new PresetInfo();

            PresetRecord pr1 = new PresetRecord("COOL SOUND",1);
            pr1.m_state = PresetRecord.PresetState.ACCEPTED;
            piMixedBag.add(pr1);

            PresetRecord pr2 = new PresetRecord("WARM SOUND",2);
            pr2.m_state = PresetRecord.PresetState.ACCEPTED;
            piMixedBag.add(pr2);

            // We want to support sparse instances of PresetInfo, so
            // this one is an example
            PresetRecord pr5 = new PresetRecord("SPARSE SOUND",5);
            pr5.m_state = PresetRecord.PresetState.ACCEPTED;
            piMixedBag.add(pr5);

            PresetRecord pr10 = new PresetRecord("NEW SOUND",10);
            pr10.m_state = PresetRecord.PresetState.TENTATIVE;
            piMixedBag.add(pr10);

            PresetRecord pr11 = new PresetRecord("NASTY SOUND",11);
            pr11.m_state = PresetRecord.PresetState.REJECTED;
            piMixedBag.add(pr11);

            return piMixedBag;
        }

        void setAmpState(
            String deviceDescription,
            String firmwareVersion,
            PresetInfo presetInfo
        );
    }
    public void acceptVisitor(IVisitor visitor) {
        visitor.setAmpState(m_deviceDescription, m_firmwareVersion, m_presetInfo);
    }
    final private MessageProtocolBase_Old m_protocolDelegate;
    final private TransportDelegateBase m_transportDelegate;
    final private DeviceDelegateBase m_deviceDelegate;


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
                m_transportDelegate = new SimulatorTransportDelegate();
                m_protocolDelegate = null;
                m_deviceDelegate = null;
                m_presetInfo = new PresetInfo();
                m_deviceDescription = "SimulatedAmplifier SN 123456";
                m_firmwareVersion = "99.00.13";
                m_presetInfo = IVisitor.piMixedBag();
                break;

            case LT40S:
                m_protocolDelegate = null;
                m_transportDelegate = null;
                m_deviceDelegate = new DeviceDelegateLT40S();
                break;

            case MMP:
            default:
                m_protocolDelegate = null;
                m_transportDelegate = null;
                throw new UnsupportedOperationException(
                    "The only simulation modes supported at present are " +
                    "SimulationMode.NO_DEVICE and SimulationMode.LT40S"
                );
        }
        if(m_deviceDelegate != null) {
            m_deviceDescription = m_deviceDelegate.m_deviceDescription;
            m_firmwareVersion = m_deviceDelegate.m_firmwareVersion;
            m_presetInfo = m_deviceDelegate.m_presetInfo;
        }
    }
    public boolean connect() {
        m_loggingAgent.appendToLog(String.format(
            "Connected to device '%s', firmware version '%s'",
            m_deviceDescription, m_firmwareVersion
        ));
        return true;
    }

    @Override
    public void switchPreset(int slotIndex) {

    }

    @Override
    public SuiteRegistry getSuiteRegistry() {
        return null;
    }

    public SuiteRecord buildPresetSuite(String suiteName, ArrayList<HashMap<String, String>> presets, Set<Integer> remainingPresetIndices) {
        return null;
    }

    @Override
    public ArrayList<SuiteRecord> loadCuratedPresetSuites() {
        return null;
    }

    @Override
    public ProviderState_e attemptConnection() {
        return ProviderState_e.PROVIDER_DEVICE_CONNECTION_SUCCEEDED;
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
