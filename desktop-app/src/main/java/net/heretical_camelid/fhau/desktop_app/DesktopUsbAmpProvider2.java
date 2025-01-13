package net.heretical_camelid.fhau.desktop_app;

import net.heretical_camelid.fhau.lib.IAmpProvider;
import net.heretical_camelid.fhau.lib.PresetInfo;

import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import java.io.PrintStream;
import java.util.regex.Pattern;

/**
 * ref: http://usb4java.org/quickstart/libusb.html
 */
public class DesktopUsbAmpProvider2
        implements IAmpProvider
{
    enum InitializationPhase {
        INITIAL,
        LIBUSB_INITIALIZED,
        DEVICE_FOUND,
        HANDLE_ACQUIRED,


    }
    Device m_fmicAmpDevice;
    DeviceHandle m_fmicAmpHandle;

    public DesktopUsbAmpProvider2(PrintStream out) {
        int libusbInitStatus = LibUsb.init(null);
        if (libusbInitStatus == LibUsb.SUCCESS) {
            out.println("libusb initialisation succeeded");
        } else {
            out.println("libusb initialisation failed with status " + libusbInitStatus);
            System.exit(1);
        }

        try {
            m_fmicAmpDevice = null;
            DeviceList list = new DeviceList();
            int result = LibUsb.getDeviceList(null, list);
            if (result < 0) throw new LibUsbException("Unable to get device list", result);
            try {
                for (Device device : list) {
                    DeviceDescriptor descriptor = new DeviceDescriptor();
                    result = LibUsb.getDeviceDescriptor(device, descriptor);
                    if (result != LibUsb.SUCCESS)
                        throw new LibUsbException("Unable to read device descriptor", result);
                    if (descriptor.idVendor() == 0x1ed8) {
                        m_fmicAmpDevice = device;
                        break;
                    }
                }
            } finally {
                // Ensure the allocated device list is freed
                LibUsb.freeDeviceList(list, true);
            }

            if (m_fmicAmpDevice != null) {
                out.println("FMIC amp found: ");
            } else {
                out.println("No FMIC amp found");
                LibUsb.exit(null);
                System.exit(2);
            }

            m_fmicAmpHandle = new DeviceHandle();
            int dhStatus = LibUsb.open(m_fmicAmpDevice, m_fmicAmpHandle);
            if(dhStatus == LibUsb.SUCCESS) {
                out.println("Device handle opened");
            } else {
                throw new LibUsbException("Unable to open device handle", dhStatus);
            }
        } catch (LibUsbException e) {
            out.println("LibUsbException caught with message " + e.getMessage());
            LibUsb.exit(null);
            System.exit(3);
        }
    }




/*



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
 */
    @Override
    public boolean connect(StringBuilder sb) {
        boolean retval = false;
/*
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
    */
        return retval;
    }

    @Override
    public void sendCommand(String commandHexString, StringBuilder sb) {
        /*
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
         */
    }

    @Override
    public void expectReports(Pattern[] reportHexStringPatterns, StringBuilder sb) {

    }

    @Override
    public PresetInfo getPresetInfo(PresetInfo requestedPresets) {
        return null;
    }
}
