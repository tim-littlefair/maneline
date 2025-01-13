package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.ByteArrayTranslator;
import net.heretical_camelid.fhau.lib.IAmpProvider;
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
    HidDevice m_fmicAmp;

    public DesktopUsbAmpProvider(PrintStream out) {
        out.println("Platform architecture: " + Platform.ARCH);
        out.println("Resource prefix: " + Platform.RESOURCE_PREFIX);
        out.println("Libusb activation: " + Platform.isLinux());
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
            out.println("Amp not attached");
        } else if (m_fmicAmp.isClosed()) {
            out.println("Amp not opened");
        } else {
            out.println("Amp opened successfully");
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

                out.println(String.format(
                        "Fender amplifier found with VID:PID=%04x:%04x serial=%s",
                        hidDevice.getVendorId(), hidDevice.getProductId(),
                        hidDevice.getSerialNumber()
                ));
                out.println(String.format("Report:\n%s",m_fmicAmp.toString()));
                break;
            }
        }
        if (m_fmicAmp == null) {
           out.println("Fender amplifier not found");
        }
         */
    }
    @Override
    public boolean connect(StringBuilder sb) {
        boolean retval = false;
        if (m_fmicAmp == null) {
            sb.append("Fender amplifier not attached");
        } else if (m_fmicAmp.isClosed()) {
            openAmpInThread();
            waitForCondition("amp opened",10000);
        } else {
            sb.append("Amp was open, has been closed, retry open");
            sb.append("Last error message: " + m_fmicAmp.getLastErrorMessage());
        }
        return retval;
    }

    private static void waitForCondition(String conditionDescription, int waitTimeMillis) {
        try {
            Thread.yield();
            System.out.println("Waiting for " + conditionDescription);
            Thread.yield();
            Thread.sleep(waitTimeMillis);
            Thread.yield();
            System.out.println("Timed out waiting for " + conditionDescription);
        } catch (InterruptedException e) {
            System.out.println("Interrupted waiting for " + conditionDescription);
        }
    }

    private void openAmpInThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean openSuccess = m_fmicAmp.open();
                if(openSuccess==true) {
                    System.out.println("open succeeded");
                } else {
                    System.out.println(
                            "open failed with message " + m_fmicAmp.getLastErrorMessage()
                    );
                }
            }
        }).start();
        System.out.println("open started");
        Thread.yield();
    }

    @Override
    public void sendCommand(String commandHexString, StringBuilder sb) {
        byte[] commandBytes = ByteArrayTranslator.hexToBytes(commandHexString);
        sb.append("Sending " + commandHexString + "\n");
        m_fmicAmp.write(commandBytes,64,(byte) 0x00,true);
        byte[] responseBytes = m_fmicAmp.readAll(1000);
        if(responseBytes!=null && responseBytes.length>0) {
            sb.append("Received " + ByteArrayTranslator.bytesToHex(responseBytes));
        } else {
            sb.append("Receive error: " + m_fmicAmp.getLastErrorMessage());
        }
    }

    @Override
    public void expectReports(Pattern[] reportHexStringPatterns, StringBuilder sb) {

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
                System.out.println("Waiting for amp to become open ...");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted...");
            }
            System.out.println("Amp has become attached");
        } else {
            System.out.println("Non-FMIC device attached: " + event.getHidDevice().toString());
        }
    }

    @Override
    public void hidDeviceDetached(HidServicesEvent event) {
        System.out.println("Amp has become detached");
    }

    @Override
    public void hidFailure(HidServicesEvent event) {

    }

    @Override
    public void hidDataReceived(HidServicesEvent event) {
        byte[] responseBytes = event.getDataReceived();
        if(responseBytes!=null && responseBytes.length>0) {
            System.out.println("hdrReceived " + ByteArrayTranslator.bytesToHex(responseBytes));
        } else {
            System.out.println("hdrReceive error: " + m_fmicAmp.getLastErrorMessage());
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            System.out.println("hdrSleep interrupted");
        }
    }
}
