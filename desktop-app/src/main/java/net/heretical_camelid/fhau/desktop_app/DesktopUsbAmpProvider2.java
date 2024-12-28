package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.ByteArrayTranslator;
import net.heretical_camelid.fhau.lib.IAmplifierProvider;
import net.heretical_camelid.fhau.lib.PresetInfo;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.Native;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesListener;
import org.hid4java.HidServicesSpecification;
import org.hid4java.event.HidServicesEvent;
import org.hid4java.jna.HidApi;

import java.io.PrintStream;

public class DesktopUsbAmpProvider
        implements IAmplifierProvider, HidServicesListener
{
    HidDevice m_fmicAmp;

    public DesktopUsbAmpProvider(PrintStream out) {
        out.println("Platform architecture: " + Platform.ARCH);
        out.println("Resource prefix: " + Platform.RESOURCE_PREFIX);
        out.println("Libusb activation: " + Platform.isLinux());
        HidServicesSpecification hidServicesSpecification = new HidServicesSpecification();
        hidServicesSpecification.setAutoStart(true);
        hidServicesSpecification.setAutoDataRead(false);
        hidServicesSpecification.setDataReadInterval(500);

        // Set the libusb variant (only needed for older Linux platforms)
        //HidApi.useLibUsbVariant = true;

        // Get HID services using custom specification
        HidServices hidServices = HidManager.getHidServices(hidServicesSpecification);
        hidServices.addHidServicesListener(this);
        // hidServices.start();

        // Look for an amp with FMIC's vendor id
        m_fmicAmp = null;
        for (HidDevice hidDevice : hidServices.getAttachedHidDevices()) {
            if(hidDevice.getVendorId()== 0x1ed8)
            //if(hidDevice.getVendorId()== 0x046d)
            {
                out.println(String.format(
                        "Fender amplifier found with VID:PID=%04x:%04x serial=%s",
                        hidDevice.getVendorId(), hidDevice.getProductId(),
                        hidDevice.getSerialNumber()
                ));
                m_fmicAmp = hidServices.getHidDevice(
                        hidDevice.getVendorId(),
                        hidDevice.getProductId(),
                        hidDevice.getSerialNumber()
                );
                out.println(String.format("Structure:\n%s",m_fmicAmp.));
                out.println(String.format("Report:\n%s",m_fmicAmp.toString()));
                break;
            }
        }
        if (m_fmicAmp == null) {
           out.println("Fender amplifier not found");
        }
    }
    @Override
    public boolean connect(StringBuilder sb) {
        boolean retval = false;
        if (m_fmicAmp == null) {
            sb.append("Fender amplifier not attached");
        } else if (m_fmicAmp.isClosed()) {
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
            try {
                System.out.println("Waiting for amp to become attached ...");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted...");
            }
        } else {
            m_fmicAmp.close();
            sb.append("Amp was open, has been closed, retry open");
            sb.append("Last error message: " + m_fmicAmp.getLastErrorMessage());
        }
        return retval;
    }

    @Override
    public byte[] sendCommandAndReceiveResponse(String commandHexString, StringBuilder sb) {
        byte[] commandBytes = ByteArrayTranslator.hexToBytes(commandHexString);
        sb.append("Sending " + commandHexString + "\n");
        m_fmicAmp.write(commandBytes,64,(byte) 0x00,true);
        byte[] responseBytes = m_fmicAmp.readAll(1000);
        if(responseBytes!=null && responseBytes.length>0) {
            sb.append("Received " + ByteArrayTranslator.bytesToHex(responseBytes));
        } else {
            sb.append("Receive error: " + m_fmicAmp.getLastErrorMessage());
        }
        return responseBytes;
    }

    @Override
    public PresetInfo getPresets(PresetInfo requestedPresets) {
        return null;
    }

    @Override
    public void hidDeviceAttached(HidServicesEvent event) {
        System.out.println("Amp has become attached");
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
