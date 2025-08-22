package net.heretical_camelid.maneline.lib;

import net.heretical_camelid.maneline.lib.interfaces.IPresetResponseReader;
import net.heretical_camelid.maneline.lib.registries.PresetRegistry;
import net.heretical_camelid.maneline.lib.registries.PresetRecord;
import net.heretical_camelid.maneline.lib.utilities.ByteArrayTranslator;
import net.heretical_camelid.maneline.lib.utilities.RawProtobufUtilities;

import java.nio.charset.StandardCharsets;
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
    static IPresetResponseReader s_presetResponseReader = null;

    int m_modalContext;
    int m_modalState;
    String m_firmwareVersion;
    String m_productIdentifier;

    int m_currentPresetIndex;
    String m_currentPresetDetails;

    final Thread m_heartbeatThread;
    boolean m_heartbeatStopped = false;

    public LTSeriesProtocol(boolean startHeartbeat) {
        m_modalContext = -1;
        m_modalState = -1;
        m_firmwareVersion = null;
        m_currentPresetIndex = -1;
        m_currentPresetDetails = null;

        m_heartbeatStopped = !startHeartbeat;
        m_heartbeatThread = new HeartbeatThread();
    }

    public int doStartup(String[] firmwareVersionEtc) {
        assert m_deviceTransport!=null;
        setLogTransactionName("txn01-startup");
        String[][] startupCommands = new String[][]{
            // First message has messageId 113, which LtAmp calls 'ModalStatusMessage'
            // I plan to raise a PR on LtAmp suggesting that this be renamed 'ModalStatusRequest'
            // and that a new message with messageId 112, name ModalStatus and the same structure,
            // should be added to the protobuf schema.
            // On LT40S, when messageId 113 is sent, the amp responds with the same messageId 113
            new String[]{"35:09:08:00:8a:07:04:08:00:10:00", "initialisation request","initRequest"},
            new String[]{"35:07:08:00:b2:06:02:08:01:00:10", "firmware version request","fwverRequest"},
        };
        int startupCommandIndex=0;
        for (String[] sc : startupCommands) {
            startupCommandIndex++;
            int scStatus = sendCommand(sc[0], sc[1],true);
            if (scStatus != STATUS_OK) {
                return scStatus;
            }
        }

        assert startupCommandIndex == 2: "Unexpected number of startup commands";
        sendProductIdentificationRequest();
        // sendBadCommand();
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
            setLogTransactionName(String.format("txn02-getPreset%03d",i));
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
        sendModalStatusRequest(TARGET_CONTEXT_SYNC_END);

        // finally request the current preset index
        setLogTransactionName("currentPresetIndexAndDetails");
        sendCurrentPresetIndexRequest();
        getStatus();
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
        setLogTransactionName("currentPresetIndex");
        appendToLog(
            "preset=" + m_currentPresetIndex,
            m_currentPresetDetails
        );
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



    int parseResponse(byte[] assembledResponseMessage) {
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
        // log(responseDescription);

        if (messageId==113) {
            // This is a response to the ModalStatusRequest message
            // sent during startup
            assert messagePbType==2;
            assert contentLength==4;
            assert contentStartOffset==5;

            assert assembledResponseMessage[5]==0x08; // param 1 of pbtype 0
            final int context = 0xff&assembledResponseMessage[6];
            assert assembledResponseMessage[7]==0x10; // param 1 of pbtype 0
            final int state = 0xff&assembledResponseMessage[8];
            log(String.format("Modal Status: context:%d state:%d",context,state));
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
                log("Firmware version: " + m_firmwareVersion);
            } else {
                assert messageId==100;
                m_productIdentifier = new String(assembledResponseMessage, 7, payloadStringLength);
                log("Product identifier: " + m_productIdentifier);
            }
        } else if (messageId==31) {
            // This is a response to a request for the JSON definition
            // of the preset with a specific index.  The preset index supplied
            // is returned as the last byte of the message.
            // If an out-of-range preset is requested the amp replies with
            // the definition of preset 1.

            assert messagePbType == 2;
            // contentLength varies, is typically around 1900-2500 bytes
            // we don't attempt to handle cases where it is less than
            // 127 bytes
            assert contentLength > 127;
            assert contentStartOffset == 6;

            assert 0x0a == assembledResponseMessage[6]; // param 1 of pbtype 2
            // bytes 7 and 8 are a varint giving the length of the JSON field
            // (this field is always long enough to require two bytes)
            int[] jsonLengthBounds = new int[]{7, 0};
            final int jsonLength = RawProtobufUtilities.extractVarint(
                assembledResponseMessage, jsonLengthBounds
            );
            assert jsonLength == contentLength - 2;
            assert jsonLengthBounds[1] == 9;

            // bytes 9 to (length-2) contain the JSON
            String jsonDefinition = new String(
                assembledResponseMessage, 9, jsonLength,
                StandardCharsets.UTF_8
            );

            assert assembledResponseMessage[9 + jsonLength] == 0x10; // param 2 of pbtype 0
            // For LT series we expect a maximum of 60 presets so the varint
            // for the preset index will be a single byte.
            int presetIndex = assembledResponseMessage[9 + jsonLength + 1];
            if (s_presetResponseReader != null) {
                s_presetResponseReader.notifyPresetResponse(
                    presetIndex, jsonDefinition
                );
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
                log(String.format(
                    "Unsupported message status received with status=%d (%s)",
                    errorType, Message200_ErrorTypes[errorType]
                ));
            } else {
                log(String.format(
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
            PresetRecord currentPresetRecord = PresetRegistry.getPresetRecord(m_currentPresetIndex);
            if(currentPresetRecord!=null) {
                String displayName = currentPresetRecord.displayName().strip().replaceAll("\\w+"," ");
                String effectDetails = currentPresetRecord.effects(
                    PresetRecord.EffectsLevelOfDetails.MODULES_AND_PARAMETERS
                );
                m_currentPresetDetails = String.format(
                    "index:%02d\nname:%s\neffects:\n%s",
                    m_currentPresetIndex,displayName,effectDetails
                );
            } else {
                m_currentPresetDetails = "No preset record found for index " + m_currentPresetIndex;
            }
            log(m_currentPresetDetails);
        } else {
            log(responseDescription);
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
        final String commandHexBytes = (
            "35:09:08:00:8a:07:04:08:" +
            String.format("%02x", context) +
            ":10:00"
        );
        return sendCommand(commandHexBytes, null,true);
    }
    private int sendProductIdentificationRequest() {
        // bytes 0: 35 => LT frame type for a single frame message
        // byte 1: 07 => length of payload
        // bytes 2-3: 08:00 => protobuf prefix
        // bytes 4-5: (two byte varint) aa:06  => 810 = 8*101 + 2
        // - message id is 101 (LtAmp's ProductIdentificationRequest)
        // - protobuf type 2 is variable length data
        // byte 6: (single byte varint) 04 => data length is 2 bytes
        // byte 7: (single byte varint) 08 => tag for param 1 of type VARINT
        // byte 8: (single byte varint) 01 => request=1
        // LT40S will respond to this command by sending
        // a message of type 100 (LtAmp's ProductIdentificationStatus)
        final String commandHexBytes = "35:07:08:00:aa:06:02:08:01";
        return sendCommand(commandHexBytes, null,true);
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
        return sendCommand(commandHexBytes, null,true);
    }
    private int sendCurrentPresetIndexRequest() {
        final String commandHexBytes = "35:07:08:00:c2:06:02:08:01";
        return sendCommand(commandHexBytes, null,true);
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
        return sendCommand(commandHexBytes, null,true);
    }

}