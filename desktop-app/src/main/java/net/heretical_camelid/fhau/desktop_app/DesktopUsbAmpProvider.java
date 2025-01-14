package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.ByteArrayTranslator;
import net.heretical_camelid.fhau.lib.DefaultLoggingAgent;
import net.heretical_camelid.fhau.lib.IAmpProvider;
import net.heretical_camelid.fhau.lib.ILoggingAgent;
import net.heretical_camelid.fhau.lib.PresetInfo;

import com.sun.jna.Platform;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesListener;
import org.hid4java.HidServicesSpecification;
import org.hid4java.event.HidServicesEvent;

import java.io.PrintStream;
import java.util.regex.Pattern;

public class DesktopUsbAmpProvider
        implements IAmpProvider, HidServicesListener
{
    final private static int VID_FMIC = 0x1ed8;
    static ILoggingAgent s_loggingAgent;
    HidDevice m_fmicAmp;

    public DesktopUsbAmpProvider() {
        s_loggingAgent = new DefaultLoggingAgent();
        
        s_loggingAgent.appendToLog(0,"Platform architecture: " + Platform.ARCH);
        s_loggingAgent.appendToLog(0,"Resource prefix: " + Platform.RESOURCE_PREFIX);
        s_loggingAgent.appendToLog(0,"Libusb activation: " + Platform.isLinux());
        HidServicesSpecification hidServicesSpecification = new HidServicesSpecification();
// /*
        hidServicesSpecification.setAutoStart(true);
        hidServicesSpecification.setAutoDataRead(false);
        hidServicesSpecification.setDataReadInterval(500);

        // Set the libusb variant (only needed for older Linux platforms)
        //HidApi.useLibUsbVariant = true;
 // */

        // Get HID services using custom specification
        HidServices hidServices = HidManager.getHidServices(hidServicesSpecification);
        hidServices.addHidServicesListener(this);
        hidServices.start();
        hidServices.scan();
        new Thread(new Runnable() {
            public void run() {
                hidServices.getAttachedHidDevices();
            }
        }).run();
        waitForCondition("device",10000);
        /*

        waitForCondition("amp attached and opened",10000);
        if (m_fmicAmp==null) {
            m_loggingAgent.appendToLog(0,"Amp not attached");
        } else if (m_fmicAmp.isClosed()) {
            m_loggingAgent.appendToLog(0,"Amp not opened");
        } else {
            m_loggingAgent.appendToLog(0,"Amp opened successfully");
        }
*/
        /*
        for (HidDevice hidDevice : hidServices.getAttachedHidDevices()) {
            if(hidDevice.getVendorId()== 0x1ed8)
            //if(hidDevice.getVendorId()== 0x046d)
            {
                HidDeviceInfoStructure hdis = new HidDeviceInfoStructure();
                hdis.vendor_id = (short) hidDevice.getVendorId();
                hdis.product_id = (short) hidDevice.getProductId();
                hdis.path = hidDevice.getPath();
                // hdis members .serial_number, .manufacturer_string and .product_string
                // do not need to be set
                hdis.release_number = (short) hidDevice.getReleaseNumber();
                hdis.usage_page = (short) hidDevice.getUsagePage();
                hdis.usage = (short) hidDevice.getUsage();
                hdis.interface_number = 0;
                m_fmicAmp = hidDevice; // new HidDevice(hdis, hidServices, new HidManager())

                m_loggingAgent.appendToLog(0,String.format(
                        "Fender amplifier found with VID:PID=%04x:%04x serial=%s",
                        hidDevice.getVendorId(), hidDevice.getProductId(),
                        hidDevice.getSerialNumber()
                ));
                m_loggingAgent.appendToLog(0,String.format("Report:\n%s",m_fmicAmp.toString()));
                break;
            }
        }
        if (m_fmicAmp == null) {
           m_loggingAgent.appendToLog(0,"Fender amplifier not found");
        }
         */
    }
    @Override
    public boolean connect() {
        boolean retval = false;
        if (m_fmicAmp == null) {
            s_loggingAgent.appendToLog(0,"Fender amplifier not attached");
        } else if (m_fmicAmp.isClosed()) {
            openAmpInThread();
            waitForCondition("amp opened",10000);
        } else {
            s_loggingAgent.appendToLog(0,"Amp was open, has been closed, retry open");
            s_loggingAgent.appendToLog(0,"Last error message: " + m_fmicAmp.getLastErrorMessage());
        }
        return retval;
    }

    private static void waitForCondition(String conditionDescription, int waitTimeMillis) {
        try {
            Thread.yield();
            s_loggingAgent.appendToLog(0,"Waiting for " + conditionDescription);
            Thread.yield();
            Thread.sleep(waitTimeMillis);
            Thread.yield();
            s_loggingAgent.appendToLog(0,"Timed out waiting for " + conditionDescription);
        } catch (InterruptedException e) {
            s_loggingAgent.appendToLog(0,"Interrupted waiting for " + conditionDescription);
        }
    }

    private void openAmpInThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean openSuccess = m_fmicAmp.open();
                if(openSuccess==true) {
                    s_loggingAgent.appendToLog(0,"open succeeded");
                } else {
                    s_loggingAgent.appendToLog(0,
                            "open failed with message " + m_fmicAmp.getLastErrorMessage()
                    );
                }
            }
        }).start();
        s_loggingAgent.appendToLog(0,"open started");
        Thread.yield();
    }

    @Override
    public void sendCommand(String commandHexString) {
        byte[] commandBytes = ByteArrayTranslator.hexToBytes(commandHexString);
        s_loggingAgent.appendToLog(0,"Sending " + commandHexString + "\n");
        m_fmicAmp.write(commandBytes,64,(byte) 0x00,true);
        byte[] responseBytes = m_fmicAmp.readAll(1000);
        if(responseBytes!=null && responseBytes.length>0) {
            s_loggingAgent.appendToLog(0,"Received " + ByteArrayTranslator.bytesToHex(responseBytes));
        } else {
            s_loggingAgent.appendToLog(0,"Receive error: " + m_fmicAmp.getLastErrorMessage());
        }
    }

    @Override
    public void expectReports(Pattern[] reportHexStringPatterns) {

    }

    @Override
    public PresetInfo getPresetInfo(PresetInfo requestedPresets) {
        return null;
    }

    @Override
    public void hidDeviceAttached(HidServicesEvent event) {
        if(event.getHidDevice().getVendorId() == VID_FMIC) {
            m_fmicAmp = event.getHidDevice();
            try {
                s_loggingAgent.appendToLog(0,"Waiting for amp to become open ...");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                s_loggingAgent.appendToLog(0,"Interrupted...");
            }
            s_loggingAgent.appendToLog(0,"Amp has become attached");
        } else {
            s_loggingAgent.appendToLog(0,"Non-FMIC device attached: " + event.getHidDevice().toString());
        }
    }

    @Override
    public void hidDeviceDetached(HidServicesEvent event) {
        s_loggingAgent.appendToLog(0,"Amp has become detached");
    }

    @Override
    public void hidFailure(HidServicesEvent event) {

    }

    @Override
    public void hidDataReceived(HidServicesEvent event) {
        byte[] responseBytes = event.getDataReceived();
        if(responseBytes!=null && responseBytes.length>0) {
            s_loggingAgent.appendToLog(0,"hdrReceived " + ByteArrayTranslator.bytesToHex(responseBytes));
        } else {
            s_loggingAgent.appendToLog(0,"hdrReceive error: " + m_fmicAmp.getLastErrorMessage());
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            s_loggingAgent.appendToLog(0,"hdrSleep interrupted");
        }
    }
}
