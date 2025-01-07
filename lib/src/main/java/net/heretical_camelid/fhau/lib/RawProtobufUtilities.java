package net.heretical_camelid.fhau.lib;

//enum MustangProtobufDialect { MPD_GUESS,  MPD_LT40S,  MPD_MMP };
public class RawProtobufUtilities {
    public static final int getMainMessageTag(byte[] pbuf_array) {
        // assert mpd==MustangProtobufDialect.MPD_LT40S;
        return 0;
    };

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
        System.out.println("HW");
    }
}
