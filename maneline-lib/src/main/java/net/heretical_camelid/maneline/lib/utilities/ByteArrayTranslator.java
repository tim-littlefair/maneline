package net.heretical_camelid.maneline.lib.utilities;

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
        StringBuilder hexSB = new StringBuilder();
        for (int i = 0; i < byteBuffer.length; ++i) {
            hexSB.append(String.format("%02x", (0xFF & (int) byteBuffer[i])));
            if(i<byteBuffer.length-1) {
                hexSB.append(":");
            }
        }
        return hexSB.toString();
    }

    public static String shortenedBytesToHex(
        byte[] byteBuffer, int minInitialBytes, int minFinalBytes
    ) {
        String fullBufferHex = bytesToHex(byteBuffer);
        // The 'shortening' will insert a 5 characters ellipsis,
        // which occupies the same space which would be occupied
        // rendering by two more bytes of hex, so we only shorten
        // if there are more than 2 bytes to remove.
        if( byteBuffer.length <= (minInitialBytes+minFinalBytes+2) ) {
            return fullBufferHex;
        } else {
            return (
                fullBufferHex.substring(0,minInitialBytes*3) +
                " ... " +
                fullBufferHex.substring(3*byteBuffer.length -1 - minFinalBytes*3)
            );
        }
    }
}
