package net.heretical_camelid.maneline.lib;

import net.heretical_camelid.maneline.lib.utilities.RawProtobufUtilities;

import org.junit.Assert;

public class LTSeriesProtocolTest {
    LTSeriesProtocol m_protocol;
    byte[] m_h2bResult;
    int[] m_viBounds;
    int m_viResult;

    int m_viExpected;
    int m_viUpperBoundExpected;

    @org.junit.Before
    public void setUp() throws Exception {
        m_protocol = new LTSeriesProtocol(false);
        m_protocol.m_modalContext = -1;
        m_protocol.m_modalState = -1;
        m_h2bResult = null;
        m_viBounds = new int[2];
        m_viResult = 0;
        m_viExpected = 0;
        m_viUpperBoundExpected = 0;
    }

    @org.junit.After
    public void tearDown() throws Exception {
        m_protocol=null;
    }

    @org.junit.Test
    public void testInitialDecodeModalStatusMessage() {
        // This test simluates the expected response to
        // the message 113 (LtAmp's ModalStatusMessage)
        // request sent to the amp during startup.
        // The amp is expected to respond with a message
        // of the same type, in which the 'context'
        // and 'state' elements both take on the value 0.
        m_h2bResult = RawProtobufUtilities.hexToBytes(
            "08:00:8a:07:04:08:00:10:00:00"
        );
        m_protocol.parseResponse(m_h2bResult);
        Assert.assertEquals(0, m_protocol.m_modalContext);
        Assert.assertEquals(0, m_protocol.m_modalState);
    }
}