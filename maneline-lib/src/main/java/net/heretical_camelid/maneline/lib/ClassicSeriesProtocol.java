package net.heretical_camelid.maneline.lib;

import static java.util.Arrays.copyOfRange;

import net.heretical_camelid.maneline.lib.interfaces.IPresetResponseReader;
import net.heretical_camelid.maneline.lib.registries.PresetRecord;
import net.heretical_camelid.maneline.lib.registries.PresetRegistry;
import net.heretical_camelid.maneline.lib.utilities.PresetJO;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;


/**
 * The class below has been heavily dependent on the following
 * reference document:
 * https://github.com/offa/plug/blob/master/doc/Technicalities.md
 * doc/Technicalities.md
 */
public class ClassicSeriesProtocol extends AbstractMessageProtocolBase {
    final boolean m_processResponsesAfterHeartbeat;

    final Thread m_heartbeatThread;
    int m_heartbeatsSentSinceLastLog = 0;
    boolean m_heartbeatStopped = false;

    int m_minPresetIndex = 1000;
    int m_maxPresetIndex = -1;

    PresetJO m_presetDefinitionBeingPopulated = null;

    ArrayList<PresetRecord> m_presetRecords = new ArrayList<>(Collections.nCopies(24,null));

    public ClassicSeriesProtocol(
        boolean startHeartbeat,
        boolean processResponsesAfterHeartbeat
    ) {
        super();
        // On the Android app, the app stops working if we check
        // for responses after a heartbeat, so we can't do that.
        // On the CLI app for Linux and other platforms, checking for
        // responses enables the CLI to discover preset changes
        // made using the LT device controls so we want to do that.
        m_processResponsesAfterHeartbeat = processResponsesAfterHeartbeat;
        m_heartbeatStopped = !startHeartbeat;
        m_heartbeatThread = new HeartbeatThread();
        m_heartbeatThread.setName("Classic-device-heartbeat");
    }

    @Override
    public int doStartup(String[] firmwareVersionEtc) {
        assert m_deviceTransport!=null;
        // This function sends packets 1 and 2 described here:
        // https://github.com/offa/plug/blob/master/doc/Technicalities.md#2-connecting
        String[][] startupCommands = new String[][]{
            new String[]{"00:c3", "initialisation request","classicInitRequest"},
            new String[]{"1a:c1", "firmware version request","classicFwverRequest"},
        };
        int startupCommandIndex=0;
        for (String[] sc : startupCommands) {
            startupCommandIndex++;
            int scStatus = sendCommand(sc[0], sc[1],true, sc[2]);
            if (scStatus != STATUS_OK) {
                setLogTransactionName(null);
                return scStatus;
            }
        }
        if(m_firmwareVersion!=null) {
            log(String.format("Firmware version: " + m_firmwareVersion));
        }
        assert startupCommandIndex == 2: "Unexpected number of startup commands";
        assert m_firmwareVersion!=null;
        firmwareVersionEtc[0] = m_firmwareVersion;

        return STATUS_OK;
    }

    @Override
    public String getFirmwareVersion() {
        return m_firmwareVersion;
    }

    @Override
    public int getPresetNamesList(PresetRegistry presetRegistry) {
        // This function sends the third packet described here:
        // https://github.com/offa/plug/blob/master/doc/Technicalities.md#2-connecting
        int scStatus = sendCommand(
            "ff:03", "preset list request",
            true, "classicPresetList"
        );

        if (scStatus != STATUS_OK) {
            return scStatus;
        }

        s_presetResponseReader = presetRegistry;
        for(int presetIndex=m_minPresetIndex; presetIndex<m_maxPresetIndex;presetIndex++ ) {
            int presetDefinitionStatus = getPresetDefinition(presetIndex, presetRegistry);
            if(presetDefinitionStatus!=STATUS_OK) {
                scStatus = STATUS_PRESET_FAIL;
            }
        }
        s_presetResponseReader = null;
        return scStatus;
    }

    @Override
    public void startHeartbeatThread() {
        if(m_heartbeatStopped) {
            log("Heartbeat thread will not be started");
        } else if(m_heartbeatThread.isAlive()) {
            log("Heartbeat thread is already started");
        } else {
            log("Starting heartbeat thread");
            m_heartbeatThread.start();
        }
    }

    @Override
    public String getStatus() {
        throw new UnsupportedOperationException(String.format(
            "%s does not support %s",
            this.getClass().getSimpleName(), "getStatus()"
        ));
    }


    @Override
    public int switchPreset(int slotIndex) {
        String transactionName = String.format("classicSelectPreset%03d", slotIndex);
        // This function sends the packet described here:
        // https://github.com/offa/plug/blob/master/doc/Technicalities.md#6-choosing-memory-bank
        int scStatus = sendCommand(
            String.format("1c:01:01:00:%02x:00:01", slotIndex),
            String.format("request to select preset %d",slotIndex),
            true,
            transactionName
        );
        return scStatus;
    }

    @Override
    public void doShutdown() {
        // do nothing
    }

    private int sendCommand(
        String commandBytesHex, String commandDescription,
        boolean responseExpected, String transactionName
    ) {
        byte[] commandBytes = new byte[64];
        colonSeparatedHexToByteArray(commandBytesHex, commandBytes);
        assert(commandDescription!=null);
        return sendCommandBytes(
            commandBytes,responseExpected, commandDescription, transactionName
        );
    }
    synchronized private int sendCommandBytes(
        byte[] commandBytes, boolean responseExpected,
        String commandDescription, String transactionName
    ) {
        // This function has some complexity related to our desire to
        // be able to fully log bytes sent, received and the response
        // parse process, but also be able to suppress this logging for
        // heartbeats which contain no interesting information.

        boolean loggingRequired;
        final int status;
        final int bytesWritten;
        final int bytesRead;
        ArrayList<String> readPhaseLogMessages = new ArrayList<String>();

        setLogTransactionName(transactionName);
        if(Thread.currentThread()!=m_heartbeatThread) {
            loggingRequired = true;
        } else {
            loggingRequired = false;
        }
        if(loggingRequired) {
            logAsHex2(commandBytes,"<");
        }
        bytesWritten = m_deviceTransport.write(commandBytes);
        if (bytesWritten < 0) {
            bytesRead=0;
            loggingRequired = true;
            status = STATUS_WRITE_FAIL;
        } else if (responseExpected == false) {
            bytesRead=0;
            loggingRequired = false;
            status = STATUS_OK;
        } else {
            bytesRead = processResponsePackets(readPhaseLogMessages, loggingRequired);
            if (bytesRead < 0) {
                loggingRequired = true;
                status = STATUS_REASSEMBLY_FAIL;
            } else {
                status = STATUS_OK;
            }
        }

        // Most heartbeat messages will not receive a response.
        // If a response is received, we do want to log the
        // message and its response
        if( (loggingRequired==false) && !readPhaseLogMessages.isEmpty() ) {
            logAsHex2(commandBytes, "<H");
            loggingRequired = true;
        }

        if (loggingRequired == true) {
            if (m_heartbeatsSentSinceLastLog > 0) {
                log(String.format(
                    "%d heartbeats sent since last message logged", m_heartbeatsSentSinceLastLog
                ));
                m_heartbeatsSentSinceLastLog = 0;
            }
            logAsHex2(commandBytes, "<");
            for(String rplm: readPhaseLogMessages) {
                log(rplm);
            }
            if(bytesWritten<0 || bytesRead<0) {
                log(String.format(
                    "Command error: write_result=%d read_result=%d status=%d transport_message=%s",
                    bytesWritten, bytesRead, status,
                    m_deviceTransport.getLastErrorMessage()
                ));
            }
            if(Thread.currentThread()==m_heartbeatThread) {
                ++m_heartbeatsSentSinceLastLog;
            }
        }
        setLogTransactionName(null);
        return status;
    }

    class HeartbeatThread extends Thread {
        @Override
        public void run() {
            while (true) {
                synchronized (m_heartbeatThread) {
                    if (m_heartbeatStopped) {
                        break;
                    }
                }
                String[] heartbeatCommand = new String[] {
                    "1c:03", // TODO: work out what to send
                    "Classic-series heartbeat"
                };
                sendCommand(
                    heartbeatCommand[0],heartbeatCommand[1],
                    m_processResponsesAfterHeartbeat,
                    "heartbeatUpdate"
                );
                try {
                    Thread.sleep(700);
                }
                catch (InterruptedException e) {
                    // expect to exit
                }
            }
        }
    }


    private int processResponsePackets(ArrayList<String> readPhaseLogMessages, boolean loggingRequired) {
        boolean messageComplete = false;
        while (messageComplete==false) {
            byte[] packetBuffer = new byte[64];
            int packetBytesRead;
            packetBytesRead = m_deviceTransport.read(packetBuffer);
            if (packetBytesRead == 0) {
                // No more response bytes available,
                // expected when response complete,
                // not an error
                messageComplete = true;
            } else if (packetBytesRead < 0) {
                readPhaseLogMessages.add(String.format(
                    "read failed, read_status=%d error=%s",
                    packetBytesRead,  m_deviceTransport.getLastErrorMessage()
                ));
                return STATUS_READ_FAIL;
            } else if (packetBytesRead != 64) {
                // USB HID packets are always exactly 64 bytes
                readPhaseLogMessages.add(String.format(
                    "read incomplete, bytes=%d error=%s buffer=%s",
                    packetBytesRead, m_deviceTransport.getLastErrorMessage(),
                    bufferToHex2(packetBuffer,"!>")
                ));
                return STATUS_READ_FAIL;
            } else {
                processPacket(readPhaseLogMessages, packetBuffer);
            }

            // As the code stands today, the code does not have any way of recognizing when all
            // packets have been received other than attempting a read and timing out with
            // an empty buffer.  At some time in the future there may be logic to recognize the
            // last packet without an needing an additional read and timeout, if/when this is
            // implemented the messageComplete boolean will be set to true.
        }

        if(loggingRequired==true) {
            for(String rplm: readPhaseLogMessages) {
                log(rplm);
            }
        }

        return STATUS_OK;
    }

    private void processPacket(ArrayList<String> readPhaseLogMessages, byte[] packet64) {
        String b2hex2 = bufferToHex2(packet64, ">");
        boolean savePreset = false;
        if (
            (m_firmwareVersion == null) &&
                b2hex2.startsWith("> [64]: 01 00") &&
                (packet64.length >= 4)
        ) {
            m_firmwareVersion = String.format("%d.%d", (int) packet64[2], (int) packet64[3]);
            readPhaseLogMessages.add(
                String.format("Firmware version packet=%s", b2hex2)
            );
        } else if (b2hex2.startsWith("> [64]: 1c 01 04 00")) {
            int presetIndex = (int) packet64[4];
            String presetName = new String(packet64, 16, 48);
            presetName = presetName.substring(0, presetName.indexOf(0));
            if( presetIndex<m_minPresetIndex) {
                m_minPresetIndex = presetIndex;
            } else if( presetIndex>m_maxPresetIndex) {
                m_maxPresetIndex = presetIndex;
            } else if (m_presetDefinitionBeingPopulated!=null) {
                readPhaseLogMessages.add(String.format(
                    "Packet containing name '%s' for preset %d (populating)=%s",
                    presetName, presetIndex, b2hex2
                ));
                m_presetDefinitionBeingPopulated.getJSONObject("info").put("displayName",presetName);
            } else {
                readPhaseLogMessages.add(String.format(
                    "Packet containing name '%s' for preset %d (new current)=%s",
                    presetName, presetIndex, b2hex2
                ));
                m_currentPresetIndex = presetIndex;
                logCurrentPresetDetails();
            }
        } else if (b2hex2.startsWith("> [64]: 1c 01 04")) {
            int dspUnitIndex = (int) packet64[3];
            int presetSlot = (int) packet64[4];
            String dspUnitName = new String(packet64, 16, 48);
            dspUnitName = dspUnitName.substring(0, dspUnitName.indexOf(0));
            if(m_presetDefinitionBeingPopulated!=null) {
                m_presetDefinitionBeingPopulated.getJSONObject("info").put(
                    "displayName", dspUnitName
                );
            }
            readPhaseLogMessages.add(String.format(
                "Packet containing name %s for dspUnit %d of preset %03d: %s",
                dspUnitName, dspUnitIndex, presetSlot, b2hex2
            ));
        } else if (b2hex2.startsWith("> [64]: 1c 01")) {
            int presetIndex = (int) packet64[4];
            String nodeFenderId = null;
            String nodeId = null;
            int effectSlot=(int) packet64[18];
            int nodeIndex;
            if(effectSlot>3) {
                nodeIndex = effectSlot;
            } else {
                // the amplifier sits between slot 3 and slot 4
                // so nodeIndex values assigned to slots 4-7 are
                // one greater than the slot number
                nodeIndex = effectSlot;
            }
            int dspPacketType = packet64[2];
            switch(dspPacketType) {
                case 5:
                    nodeId = "amp";
                    assert effectSlot==0;
                    nodeIndex = 4;
                    break;
                case 6:
                    nodeId = "stomp";
                    break;
                case 7:
                    nodeId = "mod";
                    break;
                case 8:
                    nodeId = "delay";
                    break;
                case 9:
                    nodeId = "reverb";
                    break;

                default:
                    nodeId = String.format("?dspType-%02x?", dspPacketType);
                    nodeIndex = -1;
            }
            readPhaseLogMessages.add(String.format(
                "DSP packet type for nodeId %s at nodeIndex %d: %s: ",
                nodeId, nodeIndex, b2hex2
            ));
            if(m_presetDefinitionBeingPopulated!=null) {
                if(dspPacketType==0) {
                    if (nodeFenderId == null) {
                        nodeFenderId = String.format("%s-%03d", nodeId, presetIndex);
                    }
                    m_presetRecords.add(
                        presetIndex,
                        m_presetDefinitionBeingPopulated.exportPresetRecord()
                    );
                } else if(nodeIndex!=-1) {
                    m_presetDefinitionBeingPopulated.addAudioGraphNode(
                        nodeFenderId, nodeId, null, nodeIndex
                    );
                }
            }
        } else if (b2hex2.startsWith("> [64]: 00 00 1c")) {
            // with MustangIv2 firmware 2.1, this is an empty response to a heartbeat
            // it can be ignored
        } else {
            readPhaseLogMessages.add(
                String.format("Unclassifed packet=%s", b2hex2)
            );
        }
    }

    private int getPresetDefinition(int slotIndex, PresetRegistry presetRegistry) {
        String transactionName = String.format("classicGetPresetDefinition%03d", slotIndex);
        // This function sends the packet described here:
        // https://github.com/offa/plug/blob/master/doc/Technicalities.md#6-choosing-memory-bank
        m_presetDefinitionBeingPopulated = PresetJO.create("");
        int scStatus = sendCommand(
            String.format("1c:01:01:00:%02x:00:01:00", slotIndex),
            String.format("request for definition of preset %d",slotIndex),
            true,
            transactionName
        );
        if(scStatus==STATUS_OK) {
            presetRegistry.register(
                slotIndex,
                m_presetDefinitionBeingPopulated.exportPresetRecord().displayName(),
                m_presetDefinitionBeingPopulated.toString(4).getBytes(StandardCharsets.UTF_8)
            );
        }
        m_presetDefinitionBeingPopulated = null;
        return scStatus;
    }
}