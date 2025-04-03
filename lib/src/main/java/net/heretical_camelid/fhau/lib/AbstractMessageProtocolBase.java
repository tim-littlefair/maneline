package net.heretical_camelid.fhau.lib;

import net.heretical_camelid.fhau.lib.interfaces.IDeviceTransport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class provides some shared utility functions required
 * for message protocol implementation classes, and defines
 * abstract functions for the high-level operations an
 * implementation is expected to provide.
 */
public abstract class AbstractMessageProtocolBase {

    // Abstract interface begins
    public abstract int doStartup(String[] firmwareVersionEtc);
    public abstract int getPresetNamesList();
    // future
    // public abstract String getPresetDefinition(int slotIndex);
    // public abstract int switchToPreset(int slotIndex);
    // Abstract interface ends

    // Status constants
    public static final int STATUS_OK = 0;
    public static final int STATUS_WRITE_FAIL = -101;
    public static final int STATUS_READ_FAIL = -102;
    public static final int STATUS_REASSEMBLY_FAIL = -103;
    public static final int STATUS_PARSE_FAIL = -104;
    public static final int STATUS_PRESET_FAIL = -105;
    public static final int STATUS_OTHER_FAIL = -109;
    public static final int STATUS_PRESET_WRAP_WARN = 201;

    protected IDeviceTransport m_deviceTransport;

    protected AbstractMessageProtocolBase() {
        m_deviceTransport = null;
    }

    public void setDeviceTransport(IDeviceTransport deviceTransport) {
        m_deviceTransport = deviceTransport;
    }
    protected static void log(String message) {
        System.out.println(message);
    }

    public static void colonSeparatedHexToByteArray(String colonSeparatedHex, byte[] byteArray) {
        String byteHexArray[] = colonSeparatedHex.split(":");
        assert byteArray.length >= byteHexArray.length;
        for (int i = 0; i < byteHexArray.length; ++i) {
            byteArray[i] = (byte) Integer.parseInt(byteHexArray[i], 16);
        }
    }

    // This function is based on upstream hid4java's BaseExample.printAsHex()
    // The original prints a buffer in full regardless of whether
    // it is mostly zero-filled.
    // This variant replaces trailing zero bytes with '...'
    // (if and only if at least one trailing zero byte is present).
    // This allows larger buffers to be used without blowing out
    // log files with empty bytes (e.g. for the report descriptor).
    // This variant is also suitable for use with both sent
    // and received data, and is controlled by an enablement variable
    public static boolean enable_printAsHex2=false;
    public static void printAsHex2(byte[] dataSentOrReceived, String directionChar) {
        if(enable_printAsHex2==false) {
            return;
        }
        System.out.printf("%s [%02x]:", directionChar, dataSentOrReceived.length);
        int trailingZeroByteCount = -1; // -1 signifies 'no non-zero bytes seen yet'
        for (int i=dataSentOrReceived.length-1; i>0; --i) {
            if (dataSentOrReceived[i]!=0) {
                trailingZeroByteCount = dataSentOrReceived.length - i - 1;
                break;
            }
        }
        for (int i=0; i<dataSentOrReceived.length; ++i) {
            System.out.printf(" %02x", dataSentOrReceived[i]);
            if (dataSentOrReceived.length-i==trailingZeroByteCount) {
                System.out.printf(" ...");
                break;
            }
        }
        System.out.println();
    }

    public static String getStringAttribute(
        String jsonDefinitionText, String attributeName
    ) {
        Pattern attrDefinitionPattern = Pattern.compile(
            String.format("\"%s\":[ ]*\"([^\"]*)\"", attributeName)
        );
        Matcher m = attrDefinitionPattern.matcher(jsonDefinitionText);

        if (m.find()) {
            assert m.groupCount() == 1;
            return m.group(1);
        } else {
            return null;
        }
    }

    protected static String displayName(String jsonDefinitionText) {
        String name = getStringAttribute(jsonDefinitionText,"displayName");
        if(name==null) {
            name = "_name_not_found_";
        }
        return name;
    }

    public abstract int switchPreset(int slotIndex);

    public abstract void doShutdown();
}

