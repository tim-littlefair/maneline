package net.heretical_camelid.maneline.desktop_app;

//import static net.heretical_camelid.maneline.lib.AbstractMessageProtocolBase.bufferToHex2;
//import static net.heretical_camelid.maneline.lib.AbstractMessageProtocolBase.logAsHex2;

import static net.heretical_camelid.maneline.lib.AbstractMessageProtocolBase.bufferToHex2;

import net.heretical_camelid.maneline.lib.AbstractMessageProtocolBase;
import net.heretical_camelid.maneline.lib.ClassicSeriesProtocol;
import net.heretical_camelid.maneline.lib.LTSeriesProtocol;
import net.heretical_camelid.maneline.lib.interfaces.IAmpProvider;
import net.heretical_camelid.maneline.lib.registries.PresetRegistry;
import net.heretical_camelid.maneline.lib.registries.SuiteRecord;
import net.heretical_camelid.maneline.lib.registries.SuiteRegistry;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesListener;
import org.hid4java.HidServicesSpecification;
import org.hid4java.ScanMode;
import org.hid4java.event.HidServicesEvent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.heretical_camelid.maneline.lib.registries.SlotBasedPresetSuiteExporter;

// This software is intended to interoperate with various series of
// models of digital modelling guitar amplifiers sold by Fender
// Musical Instruments Corporation (FMIC) over the period 2012
// to the present (late 2025 at the time of writing).
// The majority of these devices have product names based on
// FMIC's 'Mustang' registered trademark.

// The major series of models for which support is planned include:
// + models with names with a roman numeral I, II, III, IV or V and an
//   optional suffix v2 after the trademark Mustang, sold from around 2012
//   to 2017;
// + models with product names with a GT- prefixed model number after the
//   Mustang trademark, sold from around 2018 to 2020;
// + models under product names with a LT- prefixed model number after the
//   Mustang trademark, sold from around 2020 to present day;
// + models with product names with a GTX- prefixed model number after the
//   Mustang trademark, sold from 2020 to the present day, presumably closely
//   related to the earlier GT- series;
// + models with product names with a LTX- prefixed model number after the
//   Mustang trademark, sold from September 2025 to the present day, presumably
//   closely related to the earlier LT- series (which is still on sale at the
//   time of writing);
// + smaller form factor headphone amplifiers named Mustang Micro (sold from
//   2021) and Mustang Micro Plus (sold from 2024).
// There are also a small number of devices fitting into one or other of the
// ranges below which have names associated with different FMIC trademarks
// (e.g. Rumble, Bronco, G-DEC).

public class DesktopUsbAmpProvider implements IAmpProvider, HidServicesListener
{
    // USB devices are identified by vendor id (VID) and product id (PID)
    // All of the devices presently supported by the maneline software
    // use the following VID:
    final private static int FMIC_VID = 0x1ed8;

    // FMIC are not obliged to publish the PIDs of specific products
    // they release, the most complete listing I can find on the public Internet
    // http://www.linux-usb.org/usb.ids and only includes devices from the
    // 2012-2017 roman numeral range, all of which fall in the numeric
    // range 0x0001 to 0x001f.

    // The known devices with PID's in the 0x0001 to 0x001f range are designed
    // to interoperate with FMIC's now withdrawn FenderFUSE applications for Windows
    // and macOS.
    // If unknown devices with PID's  in this range are discovered, the software
    // will attempt to operate with them using protocols emulating FenderFUSE.
    // The term 'classic' will be used to refer to this generation of devices.
    final private static int MUSTANG_CLASSIC_PID_MIN = 0x0001;
    final private static int MUSTANG_I_V2_PID = 0x14;
    final private static int MUSTANG_CLASSIC_PID_MAX = 0x001f;

    // The LT- range consists of models with the Mustang trademark
    // prefix paired with the model numbers LT25, LT40S and LT50,
    // and also a model named Rumble LT25 (optimized for amplifying
    // bass guitars).
    final private static int MUSTANG_LT50_PID=0x0036;
    final private static int MUSTANG_LT25_PID=0x0037;
    final private static int RUMBLE_LT25_PID=0x0038;
    final private static int MUSTANG_LT40S_PID=0x0046;

    // The PIDs of both small headphone amps are known
    final private static int MUSTANG_MICRO_ORIGINAL_PID=0x0043;
    final private static int MUSTANG_MICRO_PLUS_PID=0x003a;

    // No PIDs are yet known for any of the GT-, GTX- or LTX- series
    // devices

    static WebModeLoggingAgent s_loggingAgent = null;
    AbstractMessageProtocolBase m_protocol;
    String m_firmwareVersion;
    PresetRegistry m_presetRegistry;
    SuiteRegistry m_suiteRegistry;
    HidServices m_hidServices;

    final String m_outputPath;

    public DesktopUsbAmpProvider(boolean s_webMode, String outputPath) {

        if(outputPath==null) {
            // Web mode requires an output directory
            assert s_webMode == false;
        } else if(outputPath.endsWith(".zip")) {
            // Web mode is not compatible with zip output
            assert s_webMode == false;
        } else {
            File outputDir = new File(outputPath);
            if(!outputDir.exists()) {
                outputDir.mkdirs();
            }
            assert outputDir.exists() : "Failed to create output directory";
            AbstractMessageProtocolBase.setOutputPath(outputPath);
        }
        m_outputPath = outputPath;

        assert s_loggingAgent == null;
        s_loggingAgent = new WebModeLoggingAgent();
        WebModeLoggingAgent.setSessionNameStatic(outputPath);
        AbstractMessageProtocolBase.setLoggingAgent(s_loggingAgent);
        s_loggingAgent.appendToLog("Web mode logging enabled");

        m_presetRegistry = new PresetRegistry(outputPath);
        m_suiteRegistry = new SuiteRegistry(m_presetRegistry);
        m_protocol = null;
        AbstractMessageProtocolBase.setLoggingAgent(s_loggingAgent);
    }

    void startProvider() {
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

        // Demonstrate low level traffic logging
        // HidApi.logTraffic = true;

        // Manually start HID services
        m_hidServices.start();

        // Enumerate devices looking for FMIC vendor id and LT series usage page
        HidDevice fmicDevice = null;
        for (HidDevice hidDevice : m_hidServices.getAttachedHidDevices()) {
            if (hidDevice.getVendorId() != FMIC_VID) {
                continue;
            } else if (hidDevice.getUsage() == 0x01 && hidDevice.getUsagePage() == 0xffffff00) {
                s_loggingAgent.setTransactionName("ampHidDetails");
                s_loggingAgent.appendToLog("Found FMIC device");
                s_loggingAgent.appendToLog(String.format("PID: %04x", hidDevice.getProductId()));
                s_loggingAgent.appendToLog("ProductName: " + hidDevice.getProduct());
                s_loggingAgent.appendToLog("Serial#: " + hidDevice.getSerialNumber());
                s_loggingAgent.appendToLog(String.format("Usage: %02x", hidDevice.getUsage()));
                s_loggingAgent.appendToLog(String.format("UsagePage: %08x", hidDevice.getUsagePage()));
                s_loggingAgent.setTransactionName(null);
                fmicDevice = hidDevice;
                break;
            }
        }

        if (fmicDevice == null) {
            // Shut down and rely on auto-shutdown hook to clear HidApi resources
            s_loggingAgent.appendToLog( "No FMIC device found");
        } else {
            int productId = fmicDevice.getProductId();
            String productInfo = String.format(
                "Detected FMIC device with VID/PID=%04x:%04x product='%s' path=%s",
                fmicDevice.getVendorId(), productId, fmicDevice.getProduct(), fmicDevice.getPath()
            );
            s_loggingAgent.appendToLog(productInfo);
            try {
                FileOutputStream productInfoStream = new FileOutputStream(
                    m_outputPath + "/fmic-product-info.txt"
                );
                productInfoStream.write(productInfo.getBytes(Charset.defaultCharset()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // The serial number and release are less interesting than the items
            // above so we log them in a separate message, which will not be
            // displayed on the web UI.
            s_loggingAgent.appendToLog( String.format(
                "serial#=%s release=%d",
                fmicDevice.getSerialNumber(), fmicDevice.getReleaseNumber()
            ));

            // Explore the capabilities of the device
            boolean usbRecordingSupported = true;
            boolean fenderFuseUsbProtocolSupported = false;
            boolean fenderToneUsbProtocolSupported = false;
            boolean fenderToneBleProtocolSupported = false;
            boolean requestReport = false;
            final String testStatusNote;
            switch(productId) {

                // Tested devices begin
                case MUSTANG_LT40S_PID:
                    fenderToneUsbProtocolSupported = true;
                    testStatusNote = "Mustang LT40S - tested with firmware 1.0.7 - expected to support control and recording";
                    break;

                case MUSTANG_I_V2_PID:
                    fenderFuseUsbProtocolSupported = true;
                    testStatusNote = "Mustang I v2 - tested with firmware TBD - expected to support recording only";
                    break;

                case MUSTANG_MICRO_PLUS_PID:
                    fenderToneBleProtocolSupported = true;
                    testStatusNote = String.format(
                        "Mustang Micro Plus - tested with firmware TBD - expected to support recording only",
                        fmicDevice.getProduct()
                    );
                    break;

                //TODO: Check whether this presents a USB HID interface
                //      If not, it won't be returned by the line earlier
                //      in this file containing
                //      m_hidServices.getAttachedHidDevices()
                //      so there's not much point in handing here (even
                //      though recording on this device is probably doable).
                case MUSTANG_MICRO_ORIGINAL_PID:
                    testStatusNote = (
                        "Mustang Micro (original) - tested with firmware TBD - expected to support recording only"
                    );
                    break;
                // Tested devices end

                // Devices with known PID's for which confidence is high
                case MUSTANG_LT50_PID:
                case MUSTANG_LT25_PID:
                case RUMBLE_LT25_PID:
                    fenderToneUsbProtocolSupported = true;
                    s_loggingAgent.appendToLog(String.format(
                        "%s - not tested but expected to support control and recording",
                        fmicDevice.getProduct()
                    ));
                    fenderToneUsbProtocolSupported = true;
                    requestReport = true;
                    break;

                default:
                    requestReport = true;
                    if (productId <= MUSTANG_CLASSIC_PID_MAX) {
                        s_loggingAgent.appendToLog(String.format(
                            "%s - not tested - may support recording",
                            fmicDevice.getProduct()
                        ));
                        fenderFuseUsbProtocolSupported = true;
                    } else {
                        s_loggingAgent.appendToLog(String.format(
                            "%s - not tested - may support recording",
                            fmicDevice.getProduct()
                        ));
                        fenderToneBleProtocolSupported = true;
                    }
            }

            if (requestReport) {
                askUserToSendDeviceOutcomeReport();
            }

            if (fenderToneUsbProtocolSupported == true) {
                m_protocol = new LTSeriesProtocol(true, true);
            } else if(fenderFuseUsbProtocolSupported == true) {
                m_protocol = new ClassicSeriesProtocol(true, true);
            } else {
                fmicDevice = null;
                // Shut down and rely on auto-shutdown hook to clear HidApi resources
                s_loggingAgent.appendToLog("No supported FMIC device found");
                return;
            }

            // Open the device
            if (fmicDevice.isClosed()) {
                if (!fmicDevice.open()) {
                    handleFmicDeviceOpenFailure(fmicDevice);
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
                s_loggingAgent.appendToLog(String.join(": ",
                    "FMIC device report descriptor: ",
                    bufferToHex2(reportDescriptor, "")
                ));
            }

            // Initialise the Fender Mustang/Rumble device
            handleInitialise(fmicDevice);
        }
    }

    private static void handleFmicDeviceOpenFailure(HidDevice fmicDevice) {
        String lastUsbHidError = fmicDevice.getLastErrorMessage();
        if(!lastUsbHidError.equals("Device not initialised")) {
            s_loggingAgent.appendToLog(
                "FMIC device error: " + lastUsbHidError
            );
        } else {
            System.out.println("The FMIC device could not be initialised");
            System.out.println("This may (or may not) relate to whether the user has OS-level permissions");
            System.out.println("to access USB devices.");
            String osName = System.getProperty("os.name");
            File udevRulesDir = new File("/usr/lib/udev/rules.d");
            if(udevRulesDir.exists()) {
                File manelineUdevRules = new File("/usr/lib/udev/rules.d/50-maneline.rules");
                if (!manelineUdevRules.exists()) {
                    System.out.println("You may need to modify udev rules to allow a non-root user logged in");
                    System.out.println("on the console to access USB devices.");
                    System.out.println("Maneline will drop a file called '50-maneline.rules' in the working directory.");
                    System.out.println("Use 'sudo' to copy or move this file to /usr/lib/udev/rules.d.");
                    System.out.println("Once installed, this file will permit non-root access to devices which");
                    System.out.println("have FMIC's USB vendor id.");
                    System.out.println("A reboot may be required to activate the new rules.");
                    try {
                        byte[] manelineRulesBytes = Objects.requireNonNull(
                            DesktopUsbAmpProvider.class.getResourceAsStream(
                            "/assets/50-maneline.rules"
                            )
                        ).readAllBytes();
                        FileOutputStream manelineRulesFOS = new FileOutputStream("50-maneline.rules");
                        manelineRulesFOS.write(manelineRulesBytes);
                        manelineRulesFOS.close();
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
    }

    private static void askUserToSendDeviceOutcomeReport() {
        System.out.println();
        System.out.println("The USB device you have connected to is not yet confirmed to work with Maneline.");
        System.out.println("Please consider adding a report on this device as a comment here:");
        System.out.println("https://github.com/tim-littlefair/feral-horse-amp-utils/issues/2");
        System.out.println("Contents of the report should be:");
        System.out.println("+ USB VID/PID and product name reported a few lines above this message");
        System.out.println("+ If the software reports a firmware version a few lines below this");
        System.out.println("  message, please include it in the report");
        System.out.println("+ Does the software run gracefully, and list the names of available presets?");
        System.out.println("+ If the software does not run gracefully, or exits without outputting");
        System.out.println("  lists, please include the output");
        System.out.println();
    }

    private void saveAmpDetailsJson(HidDevice fmicDevice) {
        // If we get to this point dump a JSON file in the run directory
        // which the LCD UI can consume to display the maneline software
        // version and details of maneline the connected amp
        try {
            JsonObject ampDetails = new JsonObject();
            String manelineAppVersion = getClass().getPackage().getImplementationVersion();
            if(manelineAppVersion==null) {
                manelineAppVersion="unknown";
            }
            ampDetails.add("swversion", new JsonPrimitive(manelineAppVersion));
            ampDetails.add("ampname", new JsonPrimitive(fmicDevice.getProduct()));
            ampDetails.add("fwversion", new JsonPrimitive(m_firmwareVersion));
            FileOutputStream ampDetailsStream = null;
            ampDetailsStream = new FileOutputStream("./amp-details.json");
            ampDetailsStream.write(ampDetails.toString().getBytes());
            ampDetailsStream.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        m_firmwareVersion = m_protocol.getFirmwareVersion();
        saveAmpDetailsJson(hidDevice);

        // The desktop app is used to generate curated suites of presets.
        // notify the relevant class in the library of the product name,
        // serial number and firmware version so that the source can
        // be documented.
        SlotBasedPresetSuiteExporter.setSourceDeviceDetails(String.format(
            "%s running firmware %s",
            hidDevice.getProduct(), m_firmwareVersion
        ));

        System.out.println("Requesting presets - should take about 5 seconds");
        int presetNamesStatus = m_protocol.getPresetNamesList(m_presetRegistry);
        if(startupStatus!=0 || presetNamesStatus!=0) {
            System.out.println("doStartup returned " + startupStatus);
            System.out.println("getPresetNamesList returned " + presetNamesStatus);
            System.out.println("Last error: " + hidDevice.getLastErrorMessage());
            return false;
        } else {
            m_presetRegistry.dump(m_suiteRegistry);
            System.out.println();
            m_protocol.startHeartbeatThread();
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
