package net.heretical_camelid.fhau.lib;

//enum MustangProtobufDialect { MPD_GUESS,  MPD_LT40S,  MPD_MMP };
public class RawProtobufUtilities {


    public static final int getMainMessageTag(byte[] pbuf_array) {
        // assert mpd==MustangProtobufDialect.MPD_LT40S;
        return 0;
    };

    public static byte[] hexToBytes(String hexString) {
        hexString = hexString.replace("+",""); // '+' can be used at end as a continuation signal
        assert hexString.length()%2==0;
        byte[] retval = new byte[hexString.length()/2];
        for(int i=0;i<retval.length;++i)
        {
            retval[i] = (byte) ( (0xFF) & (Integer.parseInt(hexString.substring(2*i,2*i+2),16)) );
        }
        return retval;
    }
    private static int extractVarint(byte[] pbuf_array, int viBounds[]) {
        assert viBounds[0] == viBounds[1];
        assert viBounds[0] < pbuf_array.length;
        int viValue = 0;
        int itemMultiplier = 1;
        while( ( ((byte)0x80)&pbuf_array[viBounds[1]] ) != (byte)0x00 ) {
            viValue += itemMultiplier * (0x7F&pbuf_array[viBounds[1]]);
            viBounds[1]+=1;
            itemMultiplier <<=7;
        }
        viValue += itemMultiplier*pbuf_array[viBounds[1]];
        viBounds[1]+=1;
        return viValue;
    }

    public static void main(String[] args) {
        final String TEST_MESSAGE = "08008a07040800100000";
        try {

            System.out.println("Checking hexToBytes");
            byte[] h2bResult = hexToBytes(TEST_MESSAGE);
            byte[] h2bExpected = new byte[]{
                0x08, 0x00, (byte) 0x8a, 0x07, 0x04, 0x08, 0x00, 0x10, 0x00, 0x00
            };
            assert(h2bResult.length==h2bExpected.length): String.format(
                "h2b result and expected are different lengths (actual: %d, expected: %d)",
                h2bResult.length,h2bExpected.length
            );
            for(int i=0;i<h2bResult.length;++i) {
                assert h2bResult[i]==h2bExpected[i]: String.format(
                    "h2b result differs from expected at index %d",i
                );
            }
            System.out.println("hexToBytes ok");

            System.out.println("Checking extract_varint");
            // Attempting to interpret the varint 8a 07 starting at
            // offset 2 into the message
            int viBounds[] = { 2, 2 };
            int evResult = extractVarint(h2bExpected, viBounds);
            int evExpected = 906;
            assert evResult == evExpected: String.format(
                "extractVarint returned unexpected value (actual:%d, expected:%d)",
                evResult,evExpected
            );
            // We expect that viBounds[1] has been updated as a side effect to reflect the
            // number of bytes consumed to decode the varint
            int viBounds1Expected = 4;
            assert viBounds[1] == viBounds1Expected: String.format(
                "extractVarint viBounds[1] unexpected value (actual:%d, expected:%d)",
                viBounds[1],viBounds1Expected
            );

            System.out.println("extractVarint ok");

        }
        catch(Exception e) {
            System.err.println("Exception: " + e.toString());
        }
    }
}
