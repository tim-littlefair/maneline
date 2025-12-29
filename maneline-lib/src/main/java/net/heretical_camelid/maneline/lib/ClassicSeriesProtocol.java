package net.heretical_camelid.maneline.lib;

import net.heretical_camelid.maneline.lib.interfaces.IPresetResponseReader;
import net.heretical_camelid.maneline.lib.registries.PresetRecord;
import net.heretical_camelid.maneline.lib.registries.PresetRegistry;
import net.heretical_camelid.maneline.lib.utilities.PresetJO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

// https://github.com/offa/plug/blob/master/doc/Technicalities.md
// contains some useful knowledge in relation to the implementation
// of this protocol.
public class ClassicSeriesProtocol extends AbstractMessageProtocolBase {
    static IPresetResponseReader s_presetResponseReader = null;

    final boolean m_processResponsesAfterHeartbeat;

    int m_modalContext;
    int m_modalState;
    String m_productIdentifier;

    String m_firmwareVersion = null;

    int m_currentPresetIndex;
    String m_currentPresetDetails;

    final Thread m_heartbeatThread;
    int m_heartbeatsSentSinceLastLog = 0;
    boolean m_heartbeatStopped = false;

    static String s_outputPath = null;
    public static void setOutputPath(String outputPath) {
        s_outputPath = outputPath;
    }

    ArrayList<PresetRecord> m_presetRecords = new ArrayList<>(Collections.nCopies(24,null));

    public ClassicSeriesProtocol(
        boolean startHeartbeat,
        boolean processResponsesAfterHeartbeat
    ) {
        m_modalContext = -1;
        m_modalState = -1;
        m_firmwareVersion = null;
        m_currentPresetIndex = -1;
        m_currentPresetDetails = null;

        // On the Android app, the app stops working if we check
        // for responses after a heartbeat, so we can't do that.
        // On the CLI app for Linux and other platforms, checking for
        // responses enables the CLI to discover preset changes
        // made using the LT device controls so we want to do that.
        m_processResponsesAfterHeartbeat = processResponsesAfterHeartbeat;
        m_heartbeatStopped = !startHeartbeat;
        m_heartbeatThread = new HeartbeatThread();
        m_heartbeatThread.setName("LT-device-heartbeat");
    }

    @Override
    public int doStartup(String[] firmwareVersionEtc) {
        assert m_deviceTransport!=null;
        setLogTransactionName("classicStartup");
        String[][] startupCommands = new String[][]{
            // First message has messageId 113, LtAmp's ModalStatusMessage
            // On LT40S, when messageId 113 is sent, the amp responds with the same messageId 113
            new String[]{"00:c3", "initialisation request","classicInitRequest"},
            // The second message requests the firmware version
            new String[]{"1a:c1", "firmware version request","classicFwverRequest"},
        };
        int startupCommandIndex=0;
        for (String[] sc : startupCommands) {
            startupCommandIndex++;
            int scStatus = sendCommand(sc[0], sc[1],true);
            if (scStatus != STATUS_OK) {
                setLogTransactionName(null);
                return scStatus;
            }
        }
        setLogTransactionName(null);
        if(m_firmwareVersion!=null) {
            log(String.format("Firmware version: " + m_firmwareVersion));
        }
        assert startupCommandIndex == 2: "Unexpected number of startup commands";
        assert m_firmwareVersion!=null;
        firmwareVersionEtc[0] = m_firmwareVersion;

        return STATUS_OK;
        /*
        throw new UnsupportedOperationException(String.format(
            "%s does not support %s",
            this.getClass().getSimpleName(), "doStartup(String[] firmwareVersionEtc)"
        ));
         */
    }

    @Override
    public String getFirmwareVersion() {
        return m_firmwareVersion;
    }

    @Override
    public void doShutdown() {
        throw new UnsupportedOperationException(String.format(
            "%s does not support %s",
            this.getClass().getSimpleName(), "doShutdown()"
        ));
    }

    @Override
    public int getPresetNamesList(
        int firstPreset, int lastPreset, PresetRegistry presetRegistry
    ) {
        setLogTransactionName("classicPresetList");
        int scStatus = sendCommand("ff:c1", "preset list request",true);
        setLogTransactionName(null);
        if (scStatus != STATUS_OK) {
            return scStatus;
        }
        for(int slotIndex=0; slotIndex<m_presetRecords.size();slotIndex++ ) {
            PresetRecord pr = m_presetRecords.get(slotIndex);
            if(pr!=null) {
                presetRegistry.register(slotIndex, pr.displayName(), pr.prettyJson().getBytes());
            }
        }
        return STATUS_OK;
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
        setLogTransactionName(String.format("classicSelectPreset%03d", slotIndex));
        int scStatus = sendCommand(
            String.format("1c:01:01:00:%02x:00:01", slotIndex),
            String.format("request to select preset %d",slotIndex),
            true
        );
        setLogTransactionName(null);
        return scStatus;
    }

    private int sendCommand(String commandBytesHex, String commandDescription, boolean responseExpected) {
        byte[] commandBytes = new byte[64];
        colonSeparatedHexToByteArray(commandBytesHex, commandBytes);
        assert(commandDescription!=null);
        return sendCommandBytes(commandBytes,responseExpected, commandDescription);
    }
    synchronized private int sendCommandBytes(
        byte[] commandBytes, boolean responseExpected, String commandDescription
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

        if(Thread.currentThread()!=m_heartbeatThread) {
            loggingRequired = true;
        } else {
            loggingRequired = false;
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
            ArrayList<String> responseMessages = new ArrayList<String>();
            bytesRead = readAndAssembleResponsePackets(responseMessages, loggingRequired);
            System.out.println(String.join("\n",responseMessages));
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
        if(!readPhaseLogMessages.isEmpty()) {
            loggingRequired = true;
        }


        if (loggingRequired == true) {
            if (m_heartbeatsSentSinceLastLog > 0) {
                log(String.format(
                    "%d heartbeats sent since last message logged", m_heartbeatsSentSinceLastLog
                ));
                m_heartbeatsSentSinceLastLog = 0;
            }
            if(Thread.currentThread()==m_heartbeatThread) {
                setLogTransactionName("heartbeatThreadUpdate");
            }
            log("Sending " + commandDescription);
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
                setLogTransactionName(null);
            }
        } else {
            ++m_heartbeatsSentSinceLastLog;
        }
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
                    "35:07:08:00:c9:01:02:08:01", // TODO: work out what to send
                    "Classic-series heartbeat"
                };
                sendCommand(
                    heartbeatCommand[0],heartbeatCommand[1],
                    m_processResponsesAfterHeartbeat
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


    private int readAndAssembleResponsePackets(ArrayList<String> readPhaseLogMessages, boolean loggingRequired) {
        byte[] assemblyBuffer = new byte[40960];
        int assemblyBufferOffset = 0;
        boolean messageComplete = false;
        while (true) {
            byte[] packetBuffer = new byte[64];
            int packetBytesRead;
            packetBytesRead = m_deviceTransport.read(packetBuffer);
            if (packetBytesRead == 0) {
                // No response available, not an error
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
                // Nominal case
                /*
                readPhaseLogMessages.add(String.format(
                    "packet received buffer=%s",
                    bufferToHex2(packetBuffer,">")
                ));
                 */
            }
            // All the non-nominal cases above return from the function, so if we get
            // here we are certain that we have succeeded in receiving exactly
            // one packet of 64 bytes, and that after processing this we should
            // loop round and attempt to receive another packet.
            readPhaseLogMessages.add(bufferToHex2(packetBuffer, ">"));
            int contentLength=packetBytesRead;
            System.arraycopy(packetBuffer, 0, assemblyBuffer, assemblyBufferOffset, contentLength);
            assemblyBufferOffset+=contentLength;



            /*
            assert packetBuffer[0] == 0x00;
            int packetContentStart = 3;
            int contentLength = packetBuffer[2];
            switch (packetBuffer[1]) {
                case 0x33: // first packet of multi-packet message
                    assert assemblyBufferOffset == 0;
                    assert contentLength == 0x3d;
                    messageComplete = false;
                    break;

                case 0x34: // middle packet of multi-packet message
                    assert contentLength == 0x3d;
                    messageComplete = false;
                    break;

                case 0x35: // sole packet of single-packet message
                    assert contentLength <= 0x3d;
                    messageComplete = true;
                    break;

                default:
                    return STATUS_REASSEMBLY_FAIL;
            }
            assert assemblyBufferOffset + contentLength < assemblyBuffer.length;
            System.arraycopy(packetBuffer, packetContentStart, assemblyBuffer, assemblyBufferOffset, contentLength);
            assemblyBufferOffset += contentLength;
             */

            // As the code stands today, the code does not have any way of recognizing when all
            // packets have been received other than attempting a read and timing out with
            // an empty buffer.  At some time in the future there may be logic to recognize the
            // last packet without an needing an additional read and timeout, if/when this is
            // implemented the messageComplete boolean will be set to true and exit will be here.
            if (messageComplete) {
                break;
            }
        }

        assemblyBuffer = Arrays.copyOfRange(assemblyBuffer, 0, assemblyBufferOffset);
        if(loggingRequired==true) {
            readPhaseLogMessages.add(String.format(
                "message complete, message=%s",
                bufferToHex2(assemblyBuffer, "+>")
            ));
        }

        assert (assemblyBuffer.length % 64) == 0: String.format(
            "Reassembled message length is %d, expected to be an exact multiple of 64 bytes",
            assemblyBuffer.length
        );
        while(assemblyBuffer.length>0) {
            String b2hex2 = bufferToHex2(Arrays.copyOfRange(assemblyBuffer,0,64), ">");
            if (
                (m_firmwareVersion == null) &&
                    b2hex2.startsWith("> [64]: 01 00") &&
                    (assemblyBuffer.length >= 4)
            ) {
                m_firmwareVersion = String.format("%d.%d", (int) assemblyBuffer[2], (int) assemblyBuffer[3]);
            } else if (b2hex2.startsWith("> [64]: 1c 01")) {
                int presetSlot = (int) assemblyBuffer[4];
                int whichSlotAttribute = (int) assemblyBuffer[2];
                if ((whichSlotAttribute == 4) && (m_presetRecords.get(presetSlot) == null)) {
                    String presetName = new String(assemblyBuffer, 16, 48);
                    presetName = presetName.substring(0, presetName.indexOf(0));
                    PresetJO pjo = new PresetJO(presetName);
                    PresetRecord pr = new PresetRecord(presetName, pjo.toString().getBytes());
                    m_presetRecords.add(presetSlot, pr);
                }
            }
            assemblyBuffer = Arrays.copyOfRange(assemblyBuffer, 64, assemblyBuffer.length);
        }

        // Dump the reassembled message with a distinctive direction character
        // byte[] reassembledMessage = Arrays.copyOfRange(assemblyBuffer, 0, assemblyBufferOffset);
        // readPhaseLogMessages.add(bufferToHex2(reassembledMessage, "+>"));
        // parseResponse(reassembledMessage,readPhaseLogMessages);
        return STATUS_OK;
    }


}