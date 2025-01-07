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
    private int extractVarint(byte[] pbuf_array, int viBounds[]) {
        assert viBounds[0] == viBounds[1];
        assert viBounds[0] < pbuf_array.length;
        int viValue = 0;
        while( ( 0x10&pbuf_array[viBounds[1]] ) != 0x00 ) {
            viValue <<= 7;
            viValue += (0x7F&pbuf_array[viBounds[1]]);
            ++viBounds[1];
        }
        viValue <<= 7;
        viValue += pbuf_array[viBounds[1]];
        ++viBounds[1];
        return viValue;
    }

    public static void main(String[] args) {
        try {
            System.out.println("Checking hexToBytes");
            byte[] h2bResult = hexToBytes("08008a07040800100000+");
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

        }
        catch(Exception e) {
            System.err.println("Exception: " + e.toString());
        }
    }
}
