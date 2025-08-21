package net.heretical_camelid.maneline.lib.utilities;

import org.junit.Assert;

public class RawProtobufUtilitiesTest {
    byte[] h2bResult;
    int[] viBounds;
    int viResult;
    int viExpected;
    int viUpperBoundExpected;

    @org.junit.Before
    public void setUp() throws Exception {
        h2bResult = null;
        viBounds = new int[2];
        viResult = 0;
        viExpected = 0;
        viUpperBoundExpected = 0;
    }

    @org.junit.After
    public void tearDown() throws Exception {
    }

    @org.junit.Test
    public void testExtractVarintNominal2ByteVarint() {
        // Nominal case 1:
        // two bytes varint 8a 07 starting at offset 2 into
        // message 113 (LtAmp's ModalStatusMessage)
        h2bResult = RawProtobufUtilities.hexToBytes(
            "08:00:8a:07:04:08:00:10:00:f0"
        );
        viBounds[0] = 2;
        viResult = RawProtobufUtilities.extractVarint(h2bResult, viBounds);
        viExpected = 906;
        viUpperBoundExpected = 4;
        Assert.assertEquals(
            String.format(
                "extractVarint returned unexpected value (expected:%d, actual:%d)",
                viExpected, viResult
            ), viExpected, viResult
        );
        Assert.assertEquals(
            String.format(
                "extractVarint updated viBounds[1] to unexpected value (expected:%d, actual:%d)",
                viUpperBoundExpected, viBounds[1]
            ),
            viUpperBoundExpected, viBounds[1]
        );
    }

    public void testExtractVarintNominal1ByteVarint() {
        // Nominal case 2:
        // single byte varint ba:06 starting at offset 2 into TEST_MESSAGE_2
        byte[] h2bResult2 = RawProtobufUtilities.hexToBytes(
            "08:02:ba:06070a05312e302e37"
        );
        viBounds[0] = 4;
        viResult = RawProtobufUtilities.extractVarint(h2bResult2, viBounds);
        viExpected = 4;
        viUpperBoundExpected = 5;
        Assert.assertEquals(
            String.format(
                "extractVarint returned unexpected value (expected:%d, actual:%d)",
                viExpected, viResult
            ), viExpected, viResult
        );
        Assert.assertEquals(
            String.format(
                "extractVarint updated viBounds[1] to unexpected value (expected:%d, actual:%d)",
                viUpperBoundExpected, viBounds[1]
            ),
            viUpperBoundExpected, viBounds[1]
        );
        System.out.println("extractVarint nominal ok");
    }

    @org.junit.Test
    public void testExtractVarintMessageId100() {
        // LTSeriesProtocol.java is presently mishandling response
        // messages of type 100 (LtAmp's ProductIdentificationStatus)
        byte[] h2bResult2 = RawProtobufUtilities.hexToBytes(
            "08:02:a2:06:10:0a:0e:6d:75:73:74:61:6e:67:2d:6c:74:2d:34:30:73"
        );
        viBounds[0] = 2;
        viResult = RawProtobufUtilities.extractVarint(h2bResult2, viBounds);
        viExpected = 802;
        viUpperBoundExpected = 4;
        Assert.assertEquals(
            String.format(
                "extractVarint returned unexpected value (expected:%d, actual:%d)",
                viExpected, viResult
            ), viExpected, viResult
        );
        Assert.assertEquals(
            String.format(
                "extractVarint updated viBounds[1] to unexpected value (expected:%d, actual:%d)",
                viUpperBoundExpected, viBounds[1]
            ),
            viUpperBoundExpected, viBounds[1]
        );            ;
        System.out.println("extractVarint message 100 ok");
    }

    // TODO:
    // boundary cases: varint=127,128, 16383, 16384
    // error cases:
    //     viBounds=null, viBounds.length!=2,
    //     varint incomplete at end of buffer
}