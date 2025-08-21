package net.heretical_camelid.maneline.lib.utilities;

//enum MustangProtobufDialect { MPD_GUESS,  MPD_LT40S,  MPD_MMP };
public class RawProtobufUtilities {


    public static final int getMainMessageTag(String messageAsHex) {
        final int retval;
        if(messageAsHex.substring(0,4).equals("0800")) {
            int viBounds[] = new int[] { 0x02, };
            int tagAndType = extractVarint(hexToBytes(messageAsHex),viBounds);
            assert (tagAndType & 0x07) == 2: "Unexpected main message type";
            retval = (tagAndType & 0xFFFFFFF8) >> 3;
        } else {
            throw new UnsupportedOperationException(String.format(
                "message prefix is %s, the only supported prefix at present is %s",
                messageAsHex.substring(0,4), "0800"
            ));
        }
        return retval;
    };

    public static final int pbGetElementId(
        byte[] pbMessage,
        int pbTagOffset,
        int[] pbValueBounds
    ) {
        int elementTag = 0xff & pbMessage[pbTagOffset];
        final int valueStartOffset;
        if(elementTag>0x7f)
        {
            assert(pbMessage[pbTagOffset]<=0x7f);
            elementTag &= 0x7f;
            elementTag += (0xff&pbMessage[3])<<8;
            valueStartOffset = pbTagOffset+2;
        } else {
            valueStartOffset = pbTagOffset+1;
        }
        final int elementId = (elementTag & 0xff80) >> 3;
        final int elementPbType = elementTag & 0x07;
        if(pbValueBounds!=null) {
            assert pbValueBounds.length == 2;
            // ref: definition of on-the-wire protobuf datatypes
            // in the 'Message Structure' section of this page:
            // https://protobuf.dev/programming-guides/encoding/#varints
            if(elementPbType != 2) {
                pbValueBounds[0]=valueStartOffset;
                // populating the value end offset into pbValueBounds[1]
                // is only supported for elements encoded using the ID 2 'LEN'
                // encoding.
                // Values in ID 0 'VARINT' encoding can be decoded using the
                // valueStartOffset only.
                // Values in other on-the-wire types are either fixed in size
                // or deprecated.
            } else {
                // TBD
                pbValueBounds[0]=valueStartOffset+2;
            }
        }
        return elementId;
    }

    public static byte[] hexToBytes(String hexString) {
        /*
        hexString = hexString.replace("+",""); // '+' can be used at end as a continuation signal
        assert hexString.length()%2==0;
        byte[] retval = new byte[hexString.length()/2];
        for(int i=0;i<retval.length;++i)
        {
            retval[i] = (byte) ( (0xFF) & (Integer.parseInt(hexString.substring(2*i,2*i+2),16)) );
        }
        return retval;
         */
        return ByteArrayTranslator.hexToBytes(hexString);
    }
    public static int extractVarint(byte[] pbuf_array, int viBounds[]) {
        assert viBounds.length==2;
        viBounds[1]=viBounds[0];
        int viValue = 0;
        int itemMultiplier = 1;
        while( ( ((byte)0x80)&pbuf_array[viBounds[1]] ) != (byte)0x00 ) {
            assert viBounds[1] < pbuf_array.length;
            viValue += itemMultiplier * (0x7F&pbuf_array[viBounds[1]]);
            viBounds[1]+=1;
            itemMultiplier <<=7;
        }
        viValue += itemMultiplier*pbuf_array[viBounds[1]];
        // Advance the upper bound one byte past the last value consumed
        ++viBounds[1];
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

            System.out.println("Checking extractVarint");
            // Attempting to interpret the varint 8a 07 starting at
            // offset 2 into the message
            int viBounds[] = { 2, 0 };
            int evResult = extractVarint(h2bExpected, viBounds);
            int evExpected = 906;
            int viUpperBoundExpected = 4;
            assert evResult == evExpected: String.format(
                "extractVarint returned unexpected value (actual:%d, expected:%d)",
                evResult,evExpected
            );
            assert viBounds[1] == viUpperBoundExpected: String.format(
                "extractVarint updated viBounds[1] to unexpected value (actual:%d, expected:%d)",
                viBounds[1],viUpperBoundExpected
            );
            System.out.println("extractVarint ok");

            System.out.println("checking getMainMessageTag");
            int gmmtResult = getMainMessageTag(TEST_MESSAGE);
            int gmmtExpected = 113;
            assert gmmtResult == gmmtExpected: String.format(
                "getMainMessageTag unexpected value (actual:%d, expected:%d)",
                gmmtResult,gmmtExpected
            );
            System.out.println("getMainMessageTag ok");


        }
        catch(Exception e) {
            System.err.println("Exception: " + e.toString());
        }
    }
}
