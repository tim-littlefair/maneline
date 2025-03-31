package net.heretical_camelid.fhau.lib;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// Useful reference:
// https://github.com/brentmaxwell/LtAmp/blob/main/Schema/protobuf/FenderMessageLT.proto

public class LTSeriesProtocol extends AbstractMessageProtocolBase {
    String m_firmwareVersion;
    PresetRegistryBase m_presetRegistry;

    public LTSeriesProtocol(PresetRegistryBase presetRegistry) {
        m_firmwareVersion = null;
        m_presetRegistry = presetRegistry;
    }

    public int doStartup(String[] firmwareVersionEtc) {
        assert m_deviceTransport!=null;

        String[][] startupCommands = new String[][]{
            new String[]{"35:09:08:00:8a:07:04:08:00:10", "initialisation request"},
            new String[]{"35:07:08:00:b2:06:02:08:01:00:10", "firmware version request"},
        };
        for (String[] sc : startupCommands) {
            int scStatus = sendCommand(sc[0], sc[1]);
            if (scStatus != STATUS_OK) {
                return scStatus;
            }
        }
        firmwareVersionEtc[0] = m_firmwareVersion;
        return STATUS_OK;
    }


    @Override
    public int getPresetNamesList() {
        assert m_deviceTransport!=null;
        for (int i = 1; i <= 60; ++i) {
            StringBuilder presetJsonSB = new StringBuilder();
            int psJsonStatus = getPresetJson(i, presetJsonSB);
            if (psJsonStatus != STATUS_OK) {
                return psJsonStatus;
            }
        }
        return STATUS_OK;
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

        int scStatus = sendCommand(switchPresetCommand[0],switchPresetCommand[1]);
        return scStatus;
    }

    private int sendCommand(String commandBytesHex, String commandDescription) {
        byte[] commandBytes = new byte[64];
        colonSeparatedHexToByteArray(commandBytesHex, commandBytes);
        // log( "Sending " + commandDescription);
        return sendCommandBytes(commandBytes);
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
        return sendCommandBytes(commandBytes);
    }

    private int sendCommandBytes(byte[] commandBytes) {
        printAsHex2(commandBytes, "<");
        int bytesWritten = m_deviceTransport.write(commandBytes);
        if (bytesWritten < 0) {
            log(m_deviceTransport.getLastErrorMessage());
            return STATUS_WRITE_FAIL;
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
        assert 0x02 == assembledResponseMessage[1];

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
            String presetExtendedName = AbstractMessageProtocolBase.displayName(jsonDefinition);
            m_presetRegistry.register(presetIndex, presetExtendedName, jsonDefinition.getBytes(StandardCharsets.UTF_8));
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
                log("read incomplete, error=" + m_deviceTransport.getLastErrorMessage());
                return STATUS_READ_FAIL;
            } /* else */
            {
                printAsHex2(packetBuffer, ">");
            }
            assert packetBuffer[0] == 0x00;
            int packetContentStart = 3;
            int contentLength = packetBuffer[2];
            boolean messageComplete;
            switch (packetBuffer[1]) {
                case 0x33: // first packet
                    assert assemblyBufferOffset == 0;
                    assert contentLength == 0x3d;
                    messageComplete = false;
                    break;

                case 0x34: // middle packet
                    assert contentLength == 0x3d;
                    messageComplete = false;
                    break;

                case 0x35:
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
        printAsHex2(reassembledMessage, "+>");
        parseResponse(reassembledMessage);
        return STATUS_OK;
    }

    private int getPresetJson(int i, StringBuilder presetJsonSB) {
        String presetIndexHex = String.format("%02x", i);
        String commandHexBytes = "35:07:08:00:ca:06:02:08:%1".replace("%1", presetIndexHex);
        String commandDescription = "request for JSON for preset " + i;

        return sendCommand(commandHexBytes, commandDescription);
    }
}
