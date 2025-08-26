package net.heretical_camelid.maneline.desktop_app;

import net.heretical_camelid.maneline.lib.interfaces.IAmpProvider;
import net.heretical_camelid.maneline.lib.interfaces.ILoggingAgent;
import net.heretical_camelid.maneline.lib.registries.PresetRegistry;
import net.heretical_camelid.maneline.lib.registries.SuiteRecord;
import net.heretical_camelid.maneline.lib.registries.SuiteRegistry;
import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;

import net.heretical_camelid.maneline.lib.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import net.heretical_camelid.maneline.lib.registries.SlotBasedPresetSuiteExporter;

import static net.heretical_camelid.maneline.lib.AbstractMessageProtocolBase.enable_printAsHex2;

public class DesktopUsbAmpProvider implements IAmpProvider, HidServicesListener
{
    final private static int VID_FMIC = 0x1ed8;

    static ILoggingAgent s_loggingAgent;
    AbstractMessageProtocolBase m_protocol;
    String m_firmwareVersion;
    PresetRegistry m_presetRegistry;
    SuiteRegistry m_suiteRegistry;
    HidServices m_hidServices;

    public DesktopUsbAmpProvider(boolean s_webMode, String outputPath) {

        File outputDir = new File(outputPath);
        if(!outputDir.exists()) {
            outputDir.mkdirs();
        }
        assert outputDir.exists() : "Failed to create output directory";

        if(s_loggingAgent!=null) {
            // Logging agent already exists, no need to recreate it
        } else if (s_webMode) {
            s_loggingAgent = new WebModeLoggingAgent();
            WebModeLoggingAgent.setSessionNameStatic(outputPath);
            AbstractMessageProtocolBase.setLoggingAgent(WebModeLoggingAgent.s_instance);
            s_loggingAgent.appendToLog("Web mode logging enabled");
        } else {
            s_loggingAgent = new DefaultLoggingAgent();
        }
        AbstractMessageProtocolBase.setLoggingAgent(s_loggingAgent);
        m_presetRegistry = new PresetRegistry(outputPath);
        m_suiteRegistry = new SuiteRegistry(m_presetRegistry);
        m_protocol = new LTSeriesProtocol(true,true);
    }

    void startProvider() {
        s_loggingAgent.setTransactionName("txn00-startProvider");
        // Demonstrate low level traffic logging
        // HidApi.logTraffic = true;

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

        // Get HID services using custom specification
        m_hidServices = HidManager.getHidServices(hidServicesSpecification);
        // Register for service events
        m_hidServices.addHidServicesListener(this);

        // Manually start HID services
        m_hidServices.start();

        // Enumerate devices looking for FMIC vendor id and LT series usage page
        HidDevice fmicDevice = null;
        for (HidDevice hidDevice : m_hidServices.getAttachedHidDevices()) {
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
            s_loggingAgent.appendToLog( "No FMIC device found");
        } else {
            boolean requestReport = false;
            int productId = fmicDevice.getProductId();
            s_loggingAgent.appendToLog( String.format(
                "Using FMIC device with VID/PID=%04x:%04x product='%s' path=%s",
                fmicDevice.getVendorId(), productId, fmicDevice.getProduct(), fmicDevice.getPath()
            ));
            // The serial number and release are less interesting than the items
            // above so we log them in a separate message, which will not be
            // displayed on the web UI.
            s_loggingAgent.appendToLog( String.format(
                "serial#=%s release=%d",
                fmicDevice.getSerialNumber(), fmicDevice.getReleaseNumber()
            ));
            if (productId==0x0046) {
                // Mustang LT40S - tested with firmware 1.0.7
                s_loggingAgent.appendToLog(
                    "Mustang LT40S - tested with firmware 1.0.7 - expected to work"
                );
            } else if (productId==0x0043) {
                // Original Mustang Micro - with 2024/2025 firmware this does not enumerate as a USB
                // HID Device - including it here in the distant hope that a future firmware might
                s_loggingAgent.appendToLog(
                    "Original Mustang Micro - not expected to be detected via USB HID"
                );
            } else if (productId==0x003a) {
                // Mustang Micro Plus - with 2024/2025 firmware this does not enumerate as a USB
                // HID Device - including it here in the slightly less distant hope that a future firmware might
                s_loggingAgent.appendToLog(
                    "Mustang Micro Plus - not expected to be detected via USB HID"
                );
                s_loggingAgent.appendToLog(
                    "A future version of FHAU may be able to connect to this device over BLE"
                );
            } else if(
                fmicDevice.getProduct().contains(" LT")
            ) {
                s_loggingAgent.appendToLog(
                    "Probable LT series device - not tested - may or may not work"
                );
                requestReport = true;
            } else if(
                fmicDevice.getProduct().contains(" GT")
            ) {
                s_loggingAgent.appendToLog(
                    "Probable GT/GTX series device - not expected to be detected via USB HID"
                );
                s_loggingAgent.appendToLog(
                    "A future version of FHAU may be able to connect to this device over BLE"
                );
                requestReport = true;
                // TODO?: Consider implementing a CLI switch for 'have a go anyway'?
                fmicDevice = null;
            } else if(fmicDevice.getProductId()<=15){
                s_loggingAgent.appendToLog(
                    "Older FMIC device - possibly supported by mustang-plug - disabled because not expected to work"
                );
                fmicDevice = null;
            } else {
                s_loggingAgent.appendToLog(
                    "Unrecognized FMIC device - disabled because not expected to work"
                );
                requestReport=true;
                // TODO?: Consider implementing a CLI switch for 'have a go anyway'?
                fmicDevice = null;

            }
            if (requestReport) {
                System.out.println();
                System.out.println("The USB device you have connected to is not yet confirmed to work with FHAU.");
                System.out.println("Please consider adding a report on this device as a comment here:");
                System.out.println("https://github.com/tim-littlefair/feral-horse-amp-utils/issues/2");
                System.out.println("Contents of the report should be:");
                System.out.println("+ USB VID/PID and product name reported a few lines above this message");
                System.out.println("+ If the software reports a firmware version a few lines below this");
                System.out.println("  message, please include it in the report");
                System.out.println("+ Does the software run gracefully, list preset names and amp-based");
                System.out.println("  preset suites?");
                System.out.println("+ If the software does not run gracefully, or exits without outputting");
                System.out.println("  lists, please include the output");
                System.out.println();
            }

            if (fmicDevice == null) {
                // Shut down and rely on auto-shutdown hook to clear HidApi resources
                System.out.println("No FMIC device found");
            } else {
                // Open the device
                if (fmicDevice.isClosed()) {
                    if (!fmicDevice.open()) {
                        String lastUsbHidError = fmicDevice.getLastErrorMessage();
                        if(!lastUsbHidError.equals("Device not initialised")) {
                          System.out.println("FMIC device error: " + lastUsbHidError);
                        } else {
                            System.out.println("The FMIC device could not be initialised");
                            System.out.println("This may (or may not) relate to whether the user has OS-level permissions");
                            System.out.println("to access USB devices.");
                            String osName = System.getProperty("os.name");
                            File udevRulesDir = new File("/usr/lib/udev/rules.d");
                            if(udevRulesDir.exists()) {
                                File fhauUdevRules = new File("/usr/lib/udev/rules.d/50-fhau.rules");
                                if (!fhauUdevRules.exists()) {
                                    System.out.println("You may need to modify udev rules to allow a non-root user logged in");
                                    System.out.println("on the console to access USB devices");
                                    System.out.println("FHAU will drop a file called '50-fhau.rules' in the working directory");
                                    System.out.println("Use 'sudo' to copy or move this file to /usr/lib/udev/rules.d");
                                    System.out.println("Once installed, this file will permit non-root access to devices which");
                                    System.out.println("have FMIC's USB vendor id");
                                    System.out.println("A reboot may be required to activate the new rules");
                                    try {
                                        byte[] fhauRulesBytes = DesktopUsbAmpProvider.class.getResourceAsStream(
                                            "/assets/50-fhau.rules"
                                        ).readAllBytes();
                                        FileOutputStream fhauRulesFOS = new FileOutputStream("50-fhau.rules");
                                        fhauRulesFOS.write(fhauRulesBytes);
                                        fhauRulesFOS.close();
                                    } catch (FileNotFoundException e) {
                                        throw new RuntimeException(e);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                } else {
                                    System.out.println("This system appears to have udev rules permitting members of group");
                                    System.out.println("'plugdev' to access USB devices.");
                                    System.out.println("You may need to reboot your system if these rules were only created");
                                    System.out.println("since last reboot.");
                                    System.out.println("You may need to add the current user to the group 'plugdev' if he/she");
                                    System.out.println("is not already a member.  Run the command 'groups' to find out.");
                                    System.out.println("If you change group membership, you may need to log out and log");
                                    System.out.println("back in to activate the rights of the new group membership.");
                                }
                            }
                        }
                        // Attempt to open the device failed, so we stop here
                        s_loggingAgent.setTransactionName(null);
                        return;
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
                    //System.out.println("FMIC device report descriptor: ");
                    //printAsHex2(reportDescriptor,"<");
                    enable_printAsHex2 = e_pah2_prev_state;
                }
                s_loggingAgent.setTransactionName(null);

                // Initialise the Fender Mustang/Rumble device
                handleInitialise(fmicDevice);
            }
        }
    }

    public void stopProvider() {
        m_protocol.doShutdown();
        m_hidServices.stop();
        m_hidServices.shutdown();
    }

    /**
     * @param hidDevice The device to use
     * @return True if the device is now initialised for use
     */
    private boolean handleInitialise(HidDevice hidDevice) {
        m_protocol.setDeviceTransport(new DeviceTransportHid4Java(hidDevice));
        String[] firmwareVersionEtc = new String[] { null };
        int startupStatus = m_protocol.doStartup(firmwareVersionEtc);
        m_firmwareVersion = firmwareVersionEtc[0];

        // The desktop app is used to generate curated suites of presets.
        // notify the relevant class in the library of the product name,
        // serial number and firmware version so that the source can
        // be documented.
        SlotBasedPresetSuiteExporter.setSourceDeviceDetails(String.format(
            "%s serial number %s running firmware %s",
            hidDevice.getProduct(), hidDevice.getSerialNumber(), m_firmwareVersion
        ));

        // The Mustang LT40S with firmware 1.0.7 has 60 presets
        // According to the internet, Mustang LT50, Mustang/Rumble LT25
        // had 30 presets on early firmware, but have since had updated
        // firmware released which supports 60 presets.
        // If/when we get success reports from devices other than the
        // LT40S we should update the values below to reflect the
        // exact capacity of known working model/firmware combinations.
        // Experiments with LT40S/firmware 1.0.7 show that
        // _on_that_device_firmware_combination_ there are no adverse
        // consequences of requesting a preset out of the storage range
        // of the amp - preset 1 is returned if the request is out of range.
        int firstPreset = 1;
        int lastPreset = 60;
        System.out.println(String.format(
            "Requesting presets %d-%d - should take about 5 seconds",
            firstPreset, lastPreset
        ));
        int presetNamesStatus = m_protocol.getPresetNamesList(
            firstPreset,lastPreset, m_presetRegistry
        );
        if(startupStatus!=0 || presetNamesStatus!=0) {
            System.out.println("doStartup returned " + startupStatus);
            System.out.println("getPresetNamesList returned " + presetNamesStatus);
            System.out.println("Last error: " + hidDevice.getLastErrorMessage());
            return false;
        } else {
            m_protocol.startHeartbeatThread();
            System.out.println();
            m_presetRegistry.dump(m_suiteRegistry);
            System.out.println();
        }
        return true;
    }

    // Override functions specific to this example beyond this point

    @Override
    public void hidDataReceived(HidServicesEvent event) {
        // System.out.println("hidDataReceived: " + event);
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
    public void switchPreset(int slotIndex) {
        m_protocol.switchPreset(slotIndex);
    }

    @Override
    public SuiteRegistry getSuiteRegistry() {
        return m_suiteRegistry;
    }

    @Override
    public SuiteRecord buildPresetSuite(String suiteName, ArrayList<HashMap<String, String>> presets, Set<Integer> remainingPresetIndices) {
        throw new RuntimeException(
            "DesktopUsbAmpProvider.buildPresetSuite(...) not implemented yet"
        );
    }

    @Override
    public ArrayList<SuiteRecord> loadCuratedPresetSuites() {
        return null;
    }

    @Override
    public ProviderState_e attemptConnection() {
        // The interface requires this as the Android/USB
        // provider needs to do the connection in stages
        // so that the UI can prompt to ask the user
        // for permission.
        // Desktop/USB doesn't need to do this so we
        // don't expect it to be called.
        return null;
    }

    public String getStatus() {
        return m_protocol.getStatus();
    }
}
