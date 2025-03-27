package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.benlypan.usbhid.OnUsbHidDeviceListener;
import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.*;

import java.util.ArrayList;
import java.util.HashMap;

class MainActivityError extends UnsupportedOperationException {
    public MainActivityError(String message) {
        super(message);
    }
    public void display(MainActivity mainActivity) {
        Toast.makeText(mainActivity,
            "MainActivityError: " + getLocalizedMessage(),
            Toast.LENGTH_LONG
        ).show();
    }
};

public class MainActivity
        extends AppCompatActivity
        implements PresetInfo.IVisitor
{
    final static String ACTION_USB_PERMISSION = "net.heretical_camelid.fhau.android_app.USB_PERMISSION";
    static LoggingAgent s_loggingAgent = null;
    BroadcastReceiver m_usbReceiver = null;
    PendingIntent m_permissionIntent;
    AmpManager m_ampManager = null;
    Button m_btnConnectionStatus;
    UsbManager m_usbManager = null;
    String m_backupDirectoryPath = null;
    private boolean m_connectionSucceeded=false;

    static void appendToLog(String message) {
        if(s_loggingAgent !=null) {
            s_loggingAgent.appendToLog(0,message);
        }
        System.out.println(message);
    }

    int m_lastPresetInUse = 0;

    int m_piSlotIndex = -1;
    final static int MAX_PRESET = 9;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        try {
            int itemId = item.getItemId();
            if (itemId == R.id.action_disconnect) {
                throw new MainActivityError("TODO: Implement disconnect");
            } else {
                // Items on second level menu handled here
                // These select a provider, and should only be selectable
                // if no provider is presently connected
                if(m_ampManager!=null) {
                    throw new MainActivityError(String.format(
                        "Can't select provider %s when another provider is already connected",
                        item.getTitle()
                    ));
                } else if(itemId == R.id.action_provider_sim_nodev) {
                    m_ampManager = new AmpManager();
                    m_ampManager.setProvider(new SimulatorAmpProvider(
                        s_loggingAgent,
                        SimulatorAmpProvider.SimulationMode.NO_DEVICE
                    ));
                } else if(itemId == R.id.action_provider_sim_lt40s) {
                    m_ampManager = new AmpManager();
                    m_ampManager.setProvider(new SimulatorAmpProvider(
                        s_loggingAgent,
                        SimulatorAmpProvider.SimulationMode.NO_DEVICE
                    ));
                } else if(itemId == R.id.action_provider_sim_mmp) {
                    m_ampManager = new AmpManager();
                    m_ampManager.setProvider(new SimulatorAmpProvider(
                        s_loggingAgent,
                        SimulatorAmpProvider.SimulationMode.NO_DEVICE
                    ));
                } else {
                    throw new MainActivityError(String.format(
                        "Provider %s not implemented yet",
                        item.getTitle()
                    ));
                }
                item.setChecked(true);
            }
        }
        catch(MainActivityError mae) {
            mae.display(this);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        setContentView(R.layout.activity_main);
        m_ampManager = new AmpManager();
        m_usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        TextView tvLog = (TextView) findViewById(R.id.tv_log);

        s_loggingAgent = new LoggingAgent(tvLog);

        setSupportActionBar(findViewById(R.id.toolbar_fhau));

        m_btnConnectionStatus = findViewById(R.id.btn_cxn_status);
        m_btnConnectionStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect();
                m_btnConnectionStatus.setText("Click to reconnect");
            }
        });

        requestFileStoragePermission();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(BuildConfig.DEBUG == true) {
            appendToLog("FHAU debug variant built at " + BuildConfig.BUILD_TIME);
        } else {
            appendToLog("FHAU version " + BuildConfig.VERSION_NAME);
        }
        connect();
    }

    @Override
    protected void onNewIntent(Intent theIntent) {
        super.onNewIntent(theIntent);
        appendToLog(theIntent.toString());
    }

    private void populatePresetSuiteDropdown() {
        AndroidUsbAmpProvider provider = (AndroidUsbAmpProvider)(m_ampManager.m_provider);
        assert provider!=null;
        FenderJsonPresetRegistry registry = (FenderJsonPresetRegistry)(provider.m_presetRegistry);
        assert registry!=null;
        PresetSuiteManager psm = new PresetSuiteManager(this, registry);
        ArrayList<PresetSuiteManager.PresetSuiteEntry> presetSuites =
            psm.buildPresetSuites(9,3,5)
        ;
        int itemLayoutId = R.layout.preset_suite_dropdown_item;

        ArrayList<String> suiteNames = new ArrayList<>();
        for(PresetSuiteManager.PresetSuiteEntry pse: presetSuites) {
            suiteNames.add(pse.first);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, itemLayoutId, suiteNames.toArray(new String[]{ }
        ));

        // Bind the items
        Spinner presetSuiteDropdown = (Spinner) findViewById(R.id.dropdown_preset_suites);
        presetSuiteDropdown.setOnItemSelectedListener(psm);
        adapter.setDropDownViewResource(itemLayoutId);
        presetSuiteDropdown.setAdapter(adapter);
    }

    private void connect() {
        if(m_connectionSucceeded==true) {
            appendToLog("Already connected!");
            // return;
        }
        HashMap<String, UsbDevice> usbDeviceMap = m_usbManager.getDeviceList();
        if (usbDeviceMap == null) {
            s_loggingAgent.appendToLog(0,"device map not received");
            return;
        } else if (usbDeviceMap.size() == 0) {
            s_loggingAgent.appendToLog(0, "device map empty");
            AndroidUsbAmpProvider provider = new AndroidUsbAmpProvider(s_loggingAgent, this);
            m_connectionSucceeded = provider.attemptConnection(device);
            // return;
        } else {
            for (String deviceName : usbDeviceMap.keySet()) {
                UsbDevice device = usbDeviceMap.get(deviceName);
                if(device.getVendorId()!=0x1ed8) {
                    s_loggingAgent.appendToLog(0,String.format(
                        "non-FMIC device found with vid=%04x pid=%04x",
                        device.getVendorId(), device.getProductId()
                    ));
                    continue;
                } else {
                    s_loggingAgent.appendToLog(0,String.format(
                        "FMIC device found with vid=%04x pid=%04x",
                        device.getVendorId(), device.getProductId()
                    ));
                    s_loggingAgent.appendToLog(0,
                        "FMIC product name: " + device.getProductName()
                    );
                    AndroidUsbAmpProvider provider = new AndroidUsbAmpProvider(s_loggingAgent, this);
                    m_connectionSucceeded = provider.attemptConnection(device);
                    if(m_connectionSucceeded) {
                        m_ampManager.setProvider(provider);
                        populatePresetSuiteDropdown();
                    } else {
                        if(m_usbManager!=null) {
                            requestUsbConnectionPermission();
                        }
                    }
                    // For the moment we don't attempt to handle multiple FMIC devices
                    // being connected and attempting to connect to second or later after
                    // first fails.
                    return;
                }
            }
        }
    }

    void setPresetButton(int buttonIndex, int slotId, String presetName) {
        String presetButtonName = String.format("button%d", buttonIndex);
        int buttonId = getResources().getIdentifier(
            presetButtonName, "id", getPackageName()
        );
        int buttonColor;
        String buttonText;
        float buttonAlpha;
        Button presetButton = findViewById(buttonId);
        if(presetButton==null) {
            appendToLog("Failed to find button " + presetButtonName);
            return;
        }
        if(slotId==0) {
            assert presetName==null;
            presetName = "NOT\nIN\nUSE";
            presetButton.setClickable(false);
            presetButton.setOnClickListener(null);
            presetButton.setEnabled(false);
            buttonColor = R.color.fhauGrey;
            buttonAlpha = 0.5F;
        } else {
            assert presetName!=null;
            presetButton.setClickable(true);
            presetButton.setOnClickListener((new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    m_ampManager.switchPreset(slotId);
                }
            }));
            presetButton.setEnabled(true);
            buttonColor = R.color.fhauGreen;
            buttonAlpha = 1.0F;
        }
        presetButton.setText(presetName);
        presetButton.setBackgroundColor(
            getResources().getColor(buttonColor,null)
        );
        presetButton.setAlpha(buttonAlpha);
    }

    void clearPresetButtons() {
        for(int i=1; i<=9; ++i) {
            setPresetButton(i,0,null);
        }
    }

    @Override
    public void visit(PresetRecord pr) {
        if (m_lastPresetInUse == MAX_PRESET) {
            return;
        }
        int buttonColour = R.color.fhauGrey;
        switch(pr.m_state) {
            case ACCEPTED:
                buttonColour = R.color.fhauGreen;
                break;
            case TENTATIVE:
                buttonColour = R.color.fhauAmber;
                break;
            default:
                appendToLog(String.format(
                    "Preset with slot=%d, name='%s' not offered because it is in state %s",
                    pr.m_slotNumber, pr.m_name, pr.m_state
                ));
                return;
        }
        m_lastPresetInUse++;
        String presetButtonName = String.format("button%d", m_lastPresetInUse);
        @SuppressLint("DiscouragedApi")
        int buttonId = getResources().getIdentifier(
                presetButtonName, "id", getPackageName()
        );
        Button presetButton = findViewById(buttonId);
        presetButton.setText(pr.m_name);
        presetButton.setEnabled(true);
        presetButton.setClickable(true);
        presetButton.setBackgroundColor(
            getResources().getColor(buttonColour,null)
        );
        if(m_piSlotIndex==pr.m_slotNumber) {
            presetButton.setAlpha(0.5F);
            presetButton.setOnClickListener(null);
        } else {
            presetButton.setAlpha(1.0F);
            presetButton.setOnClickListener((new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    m_ampManager.switchPreset(pr.m_slotNumber);
                }
            }));
        }
    }

    @Override
    public void setActivePresetIndex(int activePresetIndex) {

    }

    @Override
    public void addMenuProvider(@NonNull MenuProvider provider, @NonNull LifecycleOwner owner, @NonNull Lifecycle.State state) {

    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    void requestUsbConnectionPermission() {
        if(m_usbReceiver==null) {
            m_usbReceiver = new UsbBroadcastReceiver();
            appendToLog("New UBR, USB request will be done");
        } else {
            appendToLog("Existing UBR, USB request will not be done");
            return;
        }
// /*
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        m_permissionIntent = PendingIntent.getBroadcast(
            this, 0,
            new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
        );
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.EXTRA_PERMISSION_GRANTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(m_usbReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(m_usbReceiver, filter);
        }
// */
    }

    void requestFileStoragePermission() {
        // TODO: Complete this logic to enable logs and preset JSON files
        // to be exposed when the Android device is connected to a computer
        // as a USB drive.
        // The compliant purpose of this permission in is to enable
        // backup and sharing of presets.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if(Environment.isExternalStorageManager()==false) {
                m_permissionIntent = PendingIntent.getBroadcast(
                    this, 0,
                    new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE
                );
                IntentFilter filter = new IntentFilter(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                filter.addAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            }
        }
    }

    public void usbAccessPermissionGranted() {
        connect();
    }
}

