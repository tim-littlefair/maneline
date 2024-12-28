package net.heretical_camelid.fhau.lib;

/**
 * This application chooses to use a colon-separated format
 * for expressing binary data in hex.
 * This is for convenience, as some of this data is copied and pasted
 * from Wireshark JSON exports, where the hex is expressed in this format.
 */
public class ByteArrayTranslator {
    public static byte[] hexToBytes(String hexByteArrayString) {
        byte[] byteBuffer = new byte[64];
        String[] hexByteStrings = hexByteArrayString.split(":");
        for (int i = 0; i < hexByteStrings.length; ++i) {
            assert hexByteStrings[i].length() == 2;
            int byteAsInt = Integer.parseInt(hexByteStrings[i], 16);
            byteBuffer[i] = (byte) byteAsInt;
        }
        return byteBuffer;
    }

    public static String bytesToHex(byte[] byteBuffer) {
        assert byteBuffer.length == 64;
        String[] hexByteStrings = new String[64];
        for (int i = 0; i < 64; ++i) {
            hexByteStrings[i] = String.format("%02x", (0xFF & (int) byteBuffer[i]));
        }
        return String.join(":", hexByteStrings);
    }
}
