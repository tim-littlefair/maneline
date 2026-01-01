package net.heretical_camelid.maneline.lib;

import net.heretical_camelid.maneline.lib.interfaces.IPresetResponseReader;
import net.heretical_camelid.maneline.lib.registries.PresetRegistry;
import net.heretical_camelid.maneline.lib.registries.PresetRecord;
import net.heretical_camelid.maneline.lib.utilities.ByteArrayTranslator;
import net.heretical_camelid.maneline.lib.utilities.RawProtobufUtilities;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

// Useful reference:
// https://github.com/brentmaxwell/LtAmp/tree/main/Schema/protobuf
// Particularly the list of message types starting here in the current
// (as at August 2025) version of FenderMessageLT.proto:
// https://github.com/brentmaxwell/LtAmp/blob/5fea240c83708f82e09fdea23b61ec158a74de61/Schema/protobuf/FenderMessageLT.proto#L81
// For each type of message, there is an individual .proto file in the parent directory defining
// that message's structure.
// Message names in comments in that file will reflect the naming
// from this reference


public class LTSeriesProtocol extends AbstractMessageProtocolBase {
    // LtAmp's file Schema/protobuf/UnsupportedMessageStatus.proto
    // at https://github.com/brentmaxwell/LtAmp/blob/5fea240c83708f82e09fdea23b61ec158a74de61/Schema/protobuf/UnsupportedMessageStatus.proto
    // defines numeric constants for the different message types
    // expected in the message covered by that protobuf file.
    private static final String[] Message200_ErrorTypes = new String[] {
        "UNSUPPORTED", "FAILED", "INVALID_PARAM", "INVALID_NODE_ID",
        "PARAM_OUT_OF_BOUNDS", "FACTORY_RESTORE_IN_PROGRESS"
    };

    final boolean m_processResponsesAfterHeartbeat;

    int m_modalContext;
    int m_modalState;
    String m_productIdentifier;

    final Thread m_heartbeatThread;
    int m_heartbeatsSentSinceLastLog = 0;
    boolean m_heartbeatStopped = false;


    public LTSeriesProtocol(
        boolean startHeartbeat,
        boolean processResponsesAfterHeartbeat
    ) {
        super();
        m_modalContext = -1;
        m_modalState = -1;

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

    public int doStartup(String[] firmwareVersionEtc) {
        assert m_deviceTransport!=null;
        setLogTransactionName("startup");
        String[][] startupCommands = new String[][]{
            // First message has messageId 113, LtAmp's ModalStatusMessage
            // On LT40S, when messageId 113 is sent, the amp responds with the same messageId 113
            new String[]{"35:09:08:00:8a:07:04:08:00:10:00", "initialisation request","initRequest"},
            // The second message requests the firmware version
            new String[]{"35:07:08:00:b2:06:02:08:01:00:10", "firmware version request","fwverRequest"},
            // The third message requests the product identification
            new String[]{"35:07:08:00:aa:06:02:08:01", "product identification","prodidRequest"},
        };
        int startupCommandIndex=0;
        for (String[] sc : startupCommands) {
            startupCommandIndex++;
            int scStatus = sendCommand(sc[0], sc[1],true);
            if (scStatus != STATUS_OK) {
                return scStatus;
            }
        }
        setLogTransactionName(null);
        assert startupCommandIndex == 3: "Unexpected number of startup commands";

        return STATUS_OK;
    }

    @Override
    public void doShutdown() {
        setLogTransactionName("shutdown");
        log("Shutting down");
        synchronized(m_heartbeatThread) {
            log("Setting heartbeat stop flag");
            m_heartbeatStopped = true;
            log("Heartbeat stop flag set");
        }
        m_heartbeatThread.interrupt();
        log("Heartbeat thread interrupted");
        setLogTransactionName(null);
    }

    @Override
    public int getPresetNamesList(PresetRegistry presetRegistry) {
        assert m_deviceTransport!=null;
        s_presetResponseReader = presetRegistry;
        for (int i = 1; i <= 60; ++i) {
            setLogTransactionName(String.format("getPreset%03d",i));
            int psJsonStatus = sendPresetJsonRequest(i);
            setLogTransactionName(null);
            if (psJsonStatus != STATUS_OK) {
                return psJsonStatus;
            }
        }
        s_presetResponseReader = null;

        // once the presets have been retrieved,
        // send a message to the amp asking it to
        // switch to the SYNC_END context
        final int TARGET_CONTEXT_SYNC_END = 1;
        setLogTransactionName("switchToSyncEnd");
        sendModalStatusRequest(TARGET_CONTEXT_SYNC_END);
        setLogTransactionName(null);

        // finally request the current preset index
        setLogTransactionName("requestCurrentPresetIndex");
        sendCurrentPresetIndexRequest();
        setLogTransactionName(null);

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
        final String[] retval = new String[1];
        final PresetRecord[] presetRecord = new PresetRecord[1];
        s_presetResponseReader = new IPresetResponseReader() {
            @Override
            public void notifyPresetResponse(int slotIndex, String presetJson) {
                presetRecord[0] = new PresetRecord("",presetJson.getBytes());
                retval[0] = "currentPresetIndex="+slotIndex;
            }
        };
        // s_presetResponseReader = null;
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

        setLogTransactionName(String.format("requestPreset%03d",slotIndex));
        int scStatus = sendCommand(switchPresetCommand[0],switchPresetCommand[1],true);
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
            bytesRead = readAndAssembleResponsePackets(readPhaseLogMessages);
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

    int parseResponse(byte[] assembledResponseMessage, ArrayList<String> readPhaseLogMessages) {
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
        // The next assertion is usually true but not always
        // TBD work out what this means
        // assert 0x02 == assembledResponseMessage[1];

        // The next 1 or 2 bytes contain a varint
        // indicating the message id and its protobuf
        // type.
        // least significant 3 bits are required for
        // the protobuf type of the message,
        // and the most significant bit is reserved
        // by the varint encoding as an indicator of
        // whether additional bytes should be consumed,
        // so message id<16 are can be encoded in
        // a single byte varint, ids with values
        // from 16 to 16383 require two bytes
        // (we don't expect to be using tags with values
        // beyond this range)
        int[] messageTagBounds = new int[2];
        messageTagBounds[0] = 2;
        final int messageTag = RawProtobufUtilities.extractVarint(
            assembledResponseMessage,messageTagBounds
        );
        final int messageId = (messageTag & 0xfff8) >> 3;
        final int messagePbType = messageTag & 0x07;
        int contentStartOffset = messageTagBounds[1];
        final int contentLength;
        if(messagePbType==2) {
            int[] contentLengthBounds = new int[2];
            contentLengthBounds[0]=contentStartOffset;
            contentLength = RawProtobufUtilities.extractVarint(
                assembledResponseMessage,contentLengthBounds
            );
            // first content item will start after the varint
            // which represents the content length
            contentStartOffset=contentLengthBounds[1];
        } else {
            contentLength = assembledResponseMessage.length-contentStartOffset;
        }

        // Build a description of the structure of the response here
        String responseDescription = String.format(
            "Response messageId=%d messagePbType=%d contentStartOffset=%d contentLength=%d raw=%s",
            messageId,messagePbType,contentStartOffset,contentLength,
            ByteArrayTranslator.shortenedBytesToHex(
                assembledResponseMessage, 8,8)
        );

        // In normal running the description only needs to be logged for
        // messages which don't generate their own logging,
        // but it can be logged here during development by uncommenting the
        // next line
        // readPhaseLogMessages.add(responseDescription);

        if (messageId==113) {
            // This is a response to the ModalStatusRequest message
            // sent during startup
            assert messagePbType==2;
            assert contentLength==4;
            assert contentStartOffset==5;

            assert assembledResponseMessage[5]==0x08; // param 1 of pbtype 0
            m_modalContext = 0xff&assembledResponseMessage[6];
            assert assembledResponseMessage[7]==0x10; // param 1 of pbtype 0
            m_modalState = 0xff&assembledResponseMessage[8];
            readPhaseLogMessages.add(String.format(
                "Modal Status: context:%d state:%d",
                m_modalContext,m_modalState
            ));
        } else if (messageId==103 || messageId==100) {
            // This is a response to the either the firmware version request
            // or the product identifier request.
            // Both requests are sent during startup and they have similar
            // structures
            assert messagePbType==2;
            // contentLength is length of version number, which can vary
            // but we do not expect it to require two-byte varint encoding
            // and don't attempt to handle it if it does
            assert contentLength<=127;
            assert contentStartOffset==5;

            assert assembledResponseMessage[5]==0x0a; // param 1 of pbtype 2
            int payloadStringLength = 0xff & assembledResponseMessage[6];
            assert payloadStringLength == contentLength - 2;
            if(messageId==103) {
                m_firmwareVersion = new String(assembledResponseMessage, 7, payloadStringLength);
                readPhaseLogMessages.add("Firmware version: " + m_firmwareVersion);
            } else {
                assert messageId==100;
                m_productIdentifier = new String(assembledResponseMessage, 7, payloadStringLength);
                readPhaseLogMessages.add("Product identifier: " + m_productIdentifier);
            }
        } else if (messageId==31 || messageId==32) {
            // This is either of
            // - messageId 31: a response to a request for the JSON definition
            //   of the preset with a specific index; or
            // - messageId 32: a response to a request for the details of the
            //   currently selected preset.
            // In either case, the first two fields in the response
            // are the JSON description of the preset and its index.
            // In the messageId 31 case these are the only fields
            // In messageId 32 there is an additional field which tells
            // whether the current prefix is 'dirty' (i.e. being edited
            // on the LT device and not yet saved)
            assert messagePbType == 2;
            // contentLength varies, is typically around 1900-2500 bytes
            // we don't attempt to handle cases where it is less than
            // 128 bytes
            assert contentLength >= 128;
            assert contentStartOffset == 6;

            assert 0x0a == assembledResponseMessage[6]; // param 1 of pbtype 2
            // bytes 7 and 8 are a varint giving the length of the JSON field
            // (this field is always long enough to require two bytes)
            int[] jsonLengthBounds = new int[]{7, 0};
            final int jsonLength = RawProtobufUtilities.extractVarint(
                assembledResponseMessage, jsonLengthBounds
            );
            // assert jsonLength == contentLength - 2;
            assert jsonLengthBounds[1] == 9;

            // bytes 9 to (length-2) contain the JSON
            String jsonDefinition = new String(
                assembledResponseMessage, 9, jsonLength,
                StandardCharsets.UTF_8
            );

            assert assembledResponseMessage[9 + jsonLength] == 0x10; // param 2 of pbtype 0
            // For LT series we expect a maximum of 60 presets so the varint
            // for the preset index will be a single byte.
            m_currentPresetIndex = 0xff&assembledResponseMessage[9 + jsonLength + 1];
            // For the moment, we ignore the dirty bit under messageId 32

            if(messageId==31) {
                if (s_presetResponseReader != null) {
                    s_presetResponseReader.notifyPresetResponse(
                        m_currentPresetIndex, jsonDefinition
                    );
                }
            } else {
                assert messageId==32;
                logCurrentPresetDetails();
            }

            // This response is received up to 60 times so we don't log it
        } else if (messageId==200) {
            // This is a response of type UnsupportedMessageStatus
            assert messagePbType == 2;
            assert contentLength == 2;
            assert contentStartOffset == 5;

            assert assembledResponseMessage[5] == 0x08; // param 1 of pbtype 0
            final int errorType = 0xff & assembledResponseMessage[6];
            if (errorType >= 0 && errorType < Message200_ErrorTypes.length) {
                readPhaseLogMessages.add(String.format(
                    "Unsupported message status received with status=%d (%s)",
                    errorType, Message200_ErrorTypes[errorType]
                ));
            } else {
                readPhaseLogMessages.add(String.format(
                    "Unsupported message status received with undocumented status=%d",
                    errorType
                ));
            }
        } else if (messageId==37) {
            // This is a response of type currentLoadedPresetIndexStatus
            // which may be received as a response to a request of the same
            // type
            assert messagePbType==2;
            assert contentLength==2;
            assert contentStartOffset==5;

            assert assembledResponseMessage[5]==0x08; // param 1 of pbtype 0
            m_currentPresetIndex = 0xff&assembledResponseMessage[6];
            logCurrentPresetDetails();
        } else {
            readPhaseLogMessages.add(responseDescription);
        }

        return STATUS_OK;
    }

    private int readAndAssembleResponsePackets(ArrayList<String> readPhaseLogMessages) {
        byte[] assemblyBuffer = new byte[4096];
        int assemblyBufferOffset = 0;
        while (true) {
            byte[] packetBuffer = new byte[64];
            int packetBytesRead;
            packetBytesRead = m_deviceTransport.read(packetBuffer);
            if (packetBytesRead < 0) {
                readPhaseLogMessages.add(String.format(
                    "read failed, read_status=%d error=%s",
                    packetBytesRead,  m_deviceTransport.getLastErrorMessage()
                ));
                return STATUS_READ_FAIL;
            } else if (packetBytesRead == 0) {
                // No response available, not an error
                return STATUS_OK;
            } else if (packetBytesRead != 64) {
                // USB HID packets are always exactly 64 bytes
                readPhaseLogMessages.add(String.format(
                    "read incomplete, bytes=%d error=%s",
                    packetBytesRead, m_deviceTransport.getLastErrorMessage()
                ));
                return STATUS_READ_FAIL;
            } 
            // All the cases above return from the function, so if we get 
            // here we are certain that we have succeeded in receiving exactly
            // one packet of 64 bytes
            readPhaseLogMessages.add(bufferToHex2(packetBuffer, ">"));
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
        readPhaseLogMessages.add(bufferToHex2(reassembledMessage, "+>"));
        parseResponse(reassembledMessage,readPhaseLogMessages);
        return STATUS_OK;
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
                    "LT-series heartbeat"
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

    private int sendModalStatusRequest(int context) {
        // bytes 0: 35 => LT frame type for a single frame message
        // byte 1: 09 => don't know why yet
        // bytes 2-3: 08:00 => protobuf prefix
        // bytes 4-5: (two byte varint) 8a:07 => 906 = 8*113 + 2
        // - message id is 113 (LtAmp's ModalStatusMessage)
        // - protobuf type 2 is variable length data
        // byte 6: (single byte varint) 04 => data length is 2 bytes
        // byte 7: (single byte varint) 08 => tag for param 1 of type VARINT
        // byte 8: (single byte varint) context requested (to be populated using param context)
        // byte 9: (single byte varint) 10 => tag for param 2 of type VARINT
        // byte 10: (single byte varint) 00 => always 'OK' for requests
        // LT40S will respond to this command by sending
        // a message of type 112 (not documented in LtAmp, proposed name ModalStatus)
        final String commandHexBytes = (String.format(
            "35:09:08:00:8a:07:04:08:%02x:10:00", context
        ));
        return sendCommand(
            commandHexBytes,
            "request for modal status "+context,
            true
        );
    }

    private int sendPresetJsonRequest(int i) {
        // bytes 0-1: 35:00 => LT frame type for a single frame message
        // bytes 2-3: 08:00 => protobuf prefix
        // bytes 4-5: (two byte varint) ca:06 => 842 = 8*37 + 2
        // - message id 37 (LtAmp's CurrentLoadedPresetIndexStatus)
        // - protobuf type 2 is variable length data
        // byte 6: (single byte varint) 02 => data length is 2 bytes
        // byte 7: (single byte varint) derived from i
        // - the index of the preset to be dumped
        // LT40S will respond to this command by sending
        // a message of type 31 (LtAmp's PresetJSONMessage)
        final String commandHexBytes =
            "35:07:08:00:ca:06:02:08:" + String.format("%02x", i)
            ;
        return sendCommand(
            commandHexBytes,
            String.format("preset %d JSON request", i),
            true
        );
    }
    private int sendCurrentPresetIndexRequest() {
        final String commandHexBytes = "35:07:08:00:c2:06:02:08:01";
        return sendCommand(commandHexBytes, "current preset index request",true);
    }

    private int sendBadCommand() {
        // bytes 0-1: 35:00 => LT frame type for a single frame message
        // bytes 2-3: 08:00 => protobuf prefix
        // bytes 4-5: (two byte varint) ba:3e => 794 = 8*999 + 2
        // - message id 999 (not known message id)
        // - protobuf type 2 is variable length data
        // byte 6: (single byte varint) 02 => data length is 2 bytes
        // byte 7: (single byte varint) 08 => tag for param 1 of type VARINT
        // byte 8: (single byte varint) 01 => value for param 1
        // This is well formed protobuf (except for the unknown message id)
        // LT40S will respond to this command by sending
        // a message of type 31 (LtAmp's PresetJSONMessage)
        final String commandHexBytes = "35:07:08:00:ba:3e:02:08:0f";
        return sendCommand(commandHexBytes, "bad command",true);
    }
}