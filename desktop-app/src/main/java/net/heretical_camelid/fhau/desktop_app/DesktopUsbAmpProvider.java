package net.heretical_camelid.fhau.desktop_app;

import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;
import org.hid4java.jna.HidApi;

import net.heretical_camelid.fhau.lib.*;

import static net.heretical_camelid.fhau.lib.AbstractMessageProtocolBase.printAsHex2;
import static net.heretical_camelid.fhau.lib.AbstractMessageProtocolBase.enable_printAsHex2;

public class DesktopUsbAmpProvider implements IAmpProvider, HidServicesListener
{
    final private static int VID_FMIC = 0x1ed8;

    static ILoggingAgent s_loggingAgent;
    PresetRegistryBase m_presetRegistry;
    private AbstractMessageProtocolBase m_protocol;

    public DesktopUsbAmpProvider() {
        s_loggingAgent = new DefaultLoggingAgent(2);
        m_presetRegistry = new FenderJsonPresetRegistry();
        m_protocol = new LTSeriesProtocol(m_presetRegistry);
        startProvider();
    }

    private void startProvider() {
        
        // Demonstrate low level traffic logging
        HidApi.logTraffic = false;

        // Configure to use custom specification
        HidServicesSpecification hidServicesSpecification = new HidServicesSpecification();

        // Use manual start
        hidServicesSpecification.setAutoStart(false);
        // Responses will be read synchronously
        hidServicesSpecification.setAutoDataRead(false);

        // Dump parameters which are default-initialized
        ScanMode sm = hidServicesSpecification.getScanMode();
        int si = hidServicesSpecification.getScanInterval();
        int dri = hidServicesSpecification.getDataReadInterval();
        int pi = hidServicesSpecification.getPauseInterval();
        System.out.println(String.format(
           "sm=%s si=%d dri=%d pi=%d", sm, si, dri, pi
        ));

        // Get HID services using custom specification
        HidServices hidServices = HidManager.getHidServices(hidServicesSpecification);
        // Register for service events
        hidServices.addHidServicesListener(this);


        // Manually start HID services
        hidServices.start();

        // Enumerate devices looking for FMIC vendor id and LT series usage page
        HidDevice fmicDevice = null;
        for (HidDevice hidDevice : hidServices.getAttachedHidDevices()) {
            if (hidDevice.getVendorId() != VID_FMIC) {
                continue;
            }
            if (hidDevice.getUsage() == 0x01 && hidDevice.getUsagePage() == 0xffffff00) {
                fmicDevice = hidDevice;
                break;
            }
        }

        if (fmicDevice == null) {
            // Shut down and rely on auto-shutdown hook to clear HidApi resources
            System.out.println("No relevant devices attached.");
        } else {
            int productId = fmicDevice.getProductId();
            System.out.println(String.format(
                "Using FMIC device with VID/PID=%04x:%04x product='%s' serial#=%s release=%d path=%s",
                fmicDevice.getVendorId(), productId, fmicDevice.getProduct(),
                fmicDevice.getSerialNumber(), fmicDevice.getReleaseNumber(), fmicDevice.getPath()
            ));
            if (productId==0x0046) {
                // Mustang LT40S - tested with firmware 1.0.7
                System.out.println("Mustang LT40S - tested with firmware 1.0.7 - expected to work");
            } else if (productId==0x0037) {
                // Original Mustang Micro - with 2024/2025 firmware this does not enumerate as an 
                // HID Device - including it here in the distant hope that a future firmware might
                System.out.println("Original Mustang Micro - not expected to be detected via USB HID");                
            } else if (productId==0x003a) {
                // Mustang Micro Plus - with 2024/2025 firmware this does not enumerate as an 
                // HID Device - including it here in the distant hope that a future firmware might
                System.out.println("Mustang Micro Plus - not expected to be detected via USB HID (but might work with BLE HID over GATT)");                
            } else if(productId>=0x0040 && productId<0x0046) {
                // See incomplete list of VID/PIDs for Mustang products at 
                // https://github.com/offa/plug/blob/master/doc/USB.md
                // This range appears to be where the LT-series devices lie historically.    
                System.out.println("Probable LT series device - not tested - may or may not work");
            } else {
                System.out.println(
                    "Outside PID range for LT series - not tested - disabled because not expected to work"
                );
                // TODO?: Consider implementing a CLI switch for 'have a go anyway'
                fmicDevice = null;
            }

            if (fmicDevice == null) {
                // Shut down and rely on auto-shutdown hook to clear HidApi resources
                System.out.println("No relevant devices attached.");
            } else {
                // Open the device
                if (fmicDevice.isClosed()) {
                  if (!fmicDevice.open()) {
                      System.out.println("FMIC device error: " + fmicDevice.getLastErrorMessage());
                      throw new IllegalStateException("Unable to open device.");
                  }
                }

                // Perform a USB ReportDescriptor operation to determine general device capabilities
                // Reports can be up to 4096 bytes for complex devices.
                // Probably won't need this but allocate max capacity anyway.
                byte[] reportDescriptor = new byte[4096];
                if (fmicDevice.getReportDescriptor(reportDescriptor) > 0) {
                  // There is an online HTML/JS tool written by Frank Zao which can
                    // parse USB HID report descriptor.
                    // https://eleccelerator.com/usbdescreqparser/
                    // I'm not yet sure whether there is anything useful to us here.
                    // This Git repo contains a reference copy of the tool in
                    // the assets directory in case the original URL gets bit-rot.
                    boolean e_pah2_prev_state = enable_printAsHex2;
                    enable_printAsHex2 = true;
                    System.out.println("FMIC device report descriptor: ");
                    printAsHex2(reportDescriptor,"<");
                    enable_printAsHex2 = e_pah2_prev_state;
                }

                // Initialise the Fender Mustang/Rumble device
                handleInitialise(fmicDevice);
            }
        }

        hidServices.stop();
        hidServices.shutdown();
    }


    /**
     * @param hidDevice The device to use
     * @return True if the device is now initialised for use
     */
    private boolean handleInitialise(HidDevice hidDevice) {
        m_protocol.setDeviceTransport(new DeviceTransportHid4Java(hidDevice));
        int startupStatus = m_protocol.doStartup();
        System.out.println("Retrieving presets - should take < 5 seconds");
        int presetNamesStatus = m_protocol.getPresetNamesList();
        if(startupStatus!=0 || presetNamesStatus!=0) {
            System.out.println("doStartup returned " + startupStatus);
            System.out.println("getPresetNamesList returned " + presetNamesStatus);
            System.out.println("Last error: " + hidDevice.getLastErrorMessage());
            return false;
        } else {
            System.out.println();
            m_presetRegistry.dump(null);
        }
        return true;
    }

    // Override functions specific to this example beyond this point

    @Override
    public void hidDataReceived(HidServicesEvent event) {
        System.out.println("hidDataReceived: " + event);
        byte[] responseBytes = event.getDataReceived();
    }

    private int sendCommand(String commandBytesHex, String commandDescription) {
        return 0;
    }

    @Override
    public void hidDeviceAttached(HidServicesEvent event) {
        //System.out.println("hidDeviceAttached: " + event);
    }

    @Override
    public void hidDeviceDetached(HidServicesEvent event) {
        //System.out.println("hidDeviceDetached: " + event);
    }

    @Override
    public void hidFailure(HidServicesEvent event) {
        System.out.println("hidFailure: " + event);
    }

    @Override
    public PresetInfo getPresetInfo(PresetInfo requestedPresets) {
        return null;
    }

    @Override
    public boolean connect() {
        System.out.println("Connect! (unexpected)");
        return true;
    }
    @Override
    public void sendCommand(String commandHexString) {
        System.out.println("sendCommand! (unexpected)");
    }

}

