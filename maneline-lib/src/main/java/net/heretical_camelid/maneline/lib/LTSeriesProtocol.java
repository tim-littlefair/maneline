package net.heretical_camelid.maneline.lib;

import net.heretical_camelid.maneline.lib.interfaces.IPresetResponseReader;
import net.heretical_camelid.maneline.lib.registries.PresetRegistry;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// Useful reference:
// https://github.com/brentmaxwell/LtAmp/blob/main/Schema/protobuf/FenderMessageLT.proto

public class LTSeriesProtocol extends AbstractMessageProtocolBase {
    static IPresetResponseReader s_presetResponseReader = null;
    String m_firmwareVersion;
    final Thread m_heartbeatThread;
    boolean m_heartbeatStopped = false;

    public LTSeriesProtocol(boolean startHeartbeat) {
        m_firmwareVersion = null;
        m_heartbeatStopped = !startHeartbeat;
        m_heartbeatThread = new HeartbeatThread();
    }

    public int doStartup(String[] firmwareVersionEtc) {
        assert m_deviceTransport!=null;

        String[][] startupCommands = new String[][]{
            new String[]{"35:09:08:00:8a:07:04:08:00:10:00", "initialisation request","initRequest"},
            new String[]{"35:07:08:00:b2:06:02:08:01:00:10", "firmware version request","fwverRequest"},
        };
        int startupCommandIndex=0;
        for (String[] sc : startupCommands) {
            startupCommandIndex++;
            setLogTransactionName(String.format(
                "txn%02d-%s",startupCommandIndex,sc[2]
            ));
            int scStatus = sendCommand(sc[0], sc[1],true);
            if (scStatus != STATUS_OK) {
                setLogTransactionName(null);
                return scStatus;
            }
            setLogTransactionName(null);
        }

        firmwareVersionEtc[0] = m_firmwareVersion;
        assert startupCommandIndex == 2: "Unexpected number of startup commands";
        setLogTransactionName("txn03-firmwareVersionEtc");
        appendToLog(
            "Firmware version etc: " +
            String.join(",",firmwareVersionEtc),
            null
        );
        setLogTransactionName(null);

        return STATUS_OK;
    }

    @Override
    public void doShutdown() {
        //log("Shutting down");
        synchronized(m_heartbeatThread) {
            //log("Setting heartbeat stop flag");
            m_heartbeatStopped = true;
            //log("Heartbeat stop flag set");
        }
        m_heartbeatThread.interrupt();
        //log("Heartbeat thread interrupted");
    }


    @Override
    public int getPresetNamesList(
        int firstPreset, int lastPreset, PresetRegistry presetRegistry
    ) {
        assert m_deviceTransport!=null;
        s_presetResponseReader = presetRegistry;
        for (int i = firstPreset; i <= lastPreset; ++i) {
            StringBuilder presetJsonSB = new StringBuilder();
            setLogTransactionName(String.format("txn05-getPreset%03d",i));
            int psJsonStatus = getPresetJson(i, presetJsonSB);
            setLogTransactionName(null);
            if (psJsonStatus != STATUS_OK) {
                return psJsonStatus;
            }
        }
        s_presetResponseReader = null;
        return STATUS_OK;
    }

    @Override
    public void startHeartbeatThread() {
        if(m_heartbeatStopped) {
            log("Heartbeat thread will not be started");
        } else if(m_heartbeatThread.isAlive()) {
            log("Heartbeat thread is already started");
        } else {
            //log("Starting heartbeat thread");
            m_heartbeatThread.start();
        }
    }

    @Override
    public String getStatus() {
        final String[] retval = new String[1];
        s_presetResponseReader = new IPresetResponseReader() {
            @Override
            public void notifyPresetResponse(int slotIndex, String presetJson) {
                retval[0] = "currentPresetIndex="+slotIndex;
            }
        };
        StringBuilder currentPresetJsonSB = new StringBuilder();
        int psJsonStatus = getPresetJson(0, currentPresetJsonSB);
        setLogTransactionName("currentPresetIndex");
        log(retval[0]);
        setLogTransactionName(null);
        s_presetResponseReader = null;
        return retval[0];
    }

    @Override
    public int switchPreset(int slotIndex) {
        assert m_deviceTransport!=null;
        assert slotIndex>=1;
        assert slotIndex<=60;

        String slotIndexHex = String.format("%02x",slotIndex);
        String[] switchPresetCommand = new String[]{
            "35:07:08:00:8a:02:02:08:" + slotIndexHex,
            "request to activate preset at slot " + slotIndex
        };

        int scStatus = sendCommand(switchPresetCommand[0],switchPresetCommand[1],true);
        return scStatus;
    }

    private int sendCommand(String commandBytesHex, String commandDescription, boolean responseExpected) {
        byte[] commandBytes = new byte[64];
        colonSeparatedHexToByteArray(commandBytesHex, commandBytes);
        if(commandDescription!=null) {
            log("Sending " + commandDescription);
        }
        return sendCommandBytes(commandBytes,responseExpected);
    }

    private int sendSubstitutedCommand(
        String commandBytesHex, String commandDescription, int[] substitutionOffsets, byte[] substitutionBytes
    ) {
        assert substitutionOffsets.length == substitutionBytes.length;
        byte[] commandBytes = new byte[64];
        colonSeparatedHexToByteArray(commandBytesHex, commandBytes);
        for(int offsetIndex=0; offsetIndex<substitutionOffsets.length; ++offsetIndex) {
            commandBytes[substitutionOffsets[offsetIndex]]=substitutionBytes[offsetIndex];
        }
        return sendCommandBytes(commandBytes,true);
    }

    synchronized private int sendCommandBytes(byte[] commandBytes, boolean responseExpected) {
        logAsHex2(commandBytes, "<");
        int bytesWritten = m_deviceTransport.write(commandBytes);
        if (bytesWritten < 0) {
            log(m_deviceTransport.getLastErrorMessage());
            return STATUS_WRITE_FAIL;
        }
        if(responseExpected==false) {
            return STATUS_OK;
        }
        int bytesRead = readAndAssembleResponsePackets();
        if (bytesRead < 0) {
            log(m_deviceTransport.getLastErrorMessage());
            return STATUS_REASSEMBLY_FAIL;
        }
        return STATUS_OK;
    }



    private int parseResponse(byte[] assembledResponseMessage) {
        // LT series responses are broadly based on Google protobuf
        // structuring, with the opcode identifying the message type
        // expressed as a 1-byte or 2-byte varint at offset 2 in
        // the message buffer.
        // See:
        // https://github.com/brentmaxwell/LtAmp/tree/main/Schema/protobuf
        // for protobuf declarations for a wide range of messages.
        // For this implementation we choose not to use the protobuf
        // framework, for the small number of messages we need to handle
        // we rely on the consistent layout of the packets.

        // All of the responses we are seeing so far start with these two
        // bytes.
        // This is a generic protobuf header.
        assert 0x08 == assembledResponseMessage[0];
        // assert 0x02 == assembledResponseMessage[1];

        // The next 1 or 2 bytes contain a varint
        // indicating the message tag and its protobuf
        // type.
        // 3 bits are required for the protobuf type
        // so message tags<16 are can be encoded in
        // a single byte varint, tags with higher
        // values require two bytes.
        if (
            (0xba == (0xff & assembledResponseMessage[2])) &&
                (0x06 == (0xff & assembledResponseMessage[3]))
        ) {
            // This as a response to the firmware version request
            int payloadLength = assembledResponseMessage[4];
            // Next byte is protobuf tag+type for firmware version field
            assert 0x0a == assembledResponseMessage[5];
            int firmwareVersionLength = assembledResponseMessage[6];
            assert payloadLength == firmwareVersionLength + 2;

            m_firmwareVersion = new String(assembledResponseMessage, 7, firmwareVersionLength);
            log("Firmware version: " + m_firmwareVersion);
        } else if (
            (0xfa == (0xff & assembledResponseMessage[2])) &&
            (0x01 == (0xff & assembledResponseMessage[3]))
        ) {
            // This is a response to a request for the JSON definition
            // of the preset with a specific index.  The preset index supplied
            // is returned as the last byte of the message.
            // If an out-of-range preset is requested the amp replies with
            // the definition of preset 1.

            // bytes 4 and 5 are a varint giving the length of the whole message
            // (assuming that the text returned is long enough to require a two-byte
            // varint, which it always is).

            // byte 6 is protobuf tag+type for the JSON definition field
            assert 0x0a == assembledResponseMessage[6];

            // bytes 7 and 8 are a varint giving the length of the JSON field
            // (again, this field is always long enough to require two bytes)

            // bytes 9 to (length-2) contain the JSON
            String jsonDefinition = new String(
                assembledResponseMessage, 9, assembledResponseMessage.length - 9 - 2,
                StandardCharsets.UTF_8
            );
            int presetIndex = assembledResponseMessage[assembledResponseMessage.length - 1];
            // System.out.println(jsonDefinition);
            if(s_presetResponseReader!=null) {
                s_presetResponseReader.notifyPresetResponse(
                    presetIndex, jsonDefinition
                );
            }
        } else {
            log(String.format(
                "Response payload length=%d starts %02x:%02x:%02x ends %02x:%02x:%02x",
                assembledResponseMessage.length,
                assembledResponseMessage[2],assembledResponseMessage[3],assembledResponseMessage[4],
                assembledResponseMessage[assembledResponseMessage.length - 3],
                assembledResponseMessage[assembledResponseMessage.length - 2],
                assembledResponseMessage[assembledResponseMessage.length - 1]
            ));
        }

        return STATUS_OK;
    }

    private int readAndAssembleResponsePackets() {
        byte[] assemblyBuffer = new byte[4096];
        int assemblyBufferOffset = 0;
        while (true) {
            byte[] packetBuffer = new byte[64];
            int packetBytesRead;
            packetBytesRead = m_deviceTransport.read(packetBuffer);
            if (packetBytesRead < 0) {
                log("read failed, error=" + m_deviceTransport.getLastErrorMessage());
                return STATUS_READ_FAIL;
            } else if (packetBytesRead != 64) {
                log(String.format(
                    "read incomplete, bytes=%d error=%s",
                    packetBytesRead, m_deviceTransport.getLastErrorMessage()
                ));
                return STATUS_READ_FAIL;
            } /* else */
            {
                logAsHex2(packetBuffer, ">");
            }
            assert packetBuffer[0] == 0x00;
            int packetContentStart = 3;
            int contentLength = packetBuffer[2];
            boolean messageComplete;
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
            if (messageComplete) {
                break;
            }
        }

        // Dump the reassembled message with a distinctive direction character
        byte[] reassembledMessage = Arrays.copyOfRange(assemblyBuffer, 0, assemblyBufferOffset);
        logAsHex2(reassembledMessage, "+>");
        parseResponse(reassembledMessage);
        return STATUS_OK;
    }

    private int getPresetJson(int i, StringBuilder presetJsonSB) {
        final String commandHexBytes;
        if(i!=0) {
            String presetIndexHex = String.format("%02x", i);
            commandHexBytes = "35:07:08:00:ca:06:02:08:%1".replace(
                "%1",
                presetIndexHex
            );
        } else {
            commandHexBytes = "35:07:08:00:aa:02:02:08:01";
        }
        // Sending null instead of a command description suppresses
        // 60 lines of logging for the 60 preset requests sent out
        // during startup.
        return sendCommand(commandHexBytes, null,true);
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
                    "35:07:08:00:c9:01:02:08:01",
                    null
                };
                sendCommand(heartbeatCommand[0],heartbeatCommand[1],false);
                try {
                    Thread.sleep(700);
                }
                catch (InterruptedException e) {
                    // expect to exit
                }
            }
        }
    }
}