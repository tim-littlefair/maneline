package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Layout;
import android.view.ContextMenu;
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

import net.heretical_camelid.fhau.lib.AmpManager;
import net.heretical_camelid.fhau.lib.IAmpProvider;
import net.heretical_camelid.fhau.lib.PresetInfo;
import net.heretical_camelid.fhau.lib.PresetRecord;
import net.heretical_camelid.fhau.lib.SimulatorAmpProvider;

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
        implements PresetInfo.IVisitor, OnUsbHidDeviceListener
{
    AmpManager m_ampManager = null;

    LoggingAgent m_loggingAgent = null;
    Button m_btnConnectionStatus;

    void appendToLog(String message) {
        if(m_loggingAgent!=null) {
            m_loggingAgent.appendToLog(0,message);
        }
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
                    m_ampManager = new AmpManager(new SimulatorAmpProvider(
                        m_loggingAgent,
                        SimulatorAmpProvider.SimulationMode.NO_DEVICE
                    ));
                } else if(itemId == R.id.action_provider_sim_lt40s) {
                    m_ampManager = new AmpManager(new SimulatorAmpProvider(
                        m_loggingAgent,
                        SimulatorAmpProvider.SimulationMode.LT40S
                    ));
                } else if(itemId == R.id.action_provider_sim_mmp) {
                    m_ampManager = new AmpManager(new SimulatorAmpProvider(
                        m_loggingAgent,
                        SimulatorAmpProvider.SimulationMode.MMP
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
        setContentView(R.layout.activity_main);

        m_ampManager = null;

        TextView tvLog = (TextView) findViewById(R.id.tv_log);

        m_loggingAgent = new LoggingAgent(tvLog);
        appendToLog("Starting up");

        setSupportActionBar(findViewById(R.id.toolbar_fhau));

        m_btnConnectionStatus = findViewById(R.id.btn_cxn_status);
        m_btnConnectionStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_btnConnectionStatus.setOnClickListener(null);
                connect();
                m_btnConnectionStatus.setText("CONNECTED!");
            }
        });

        populatePresetSuiteDropdown();
    }

    private void populatePresetSuiteDropdown() {
        int itemLayoutId = R.layout.preset_suite_dropdown_item;
        //int itemLayoutId = android.R.layout.simple_spinner_dropdown_item;

        // Create an ArrayAdapter for the Spinner
        String[] items = new String[] {
            "No preset suite selected",
            "Presets using amplifier LinearGain"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, itemLayoutId, items);

        // Bind the items
        Spinner presetSuiteDropdown = (Spinner) findViewById(R.id.dropdown_preset_suites);
        adapter.setDropDownViewResource(itemLayoutId);
        presetSuiteDropdown.setAdapter(adapter);
    }
/*
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        //Menu menu = (Menu) findViewById(R.id.menu_providers);
        menu.setGroupCheckable(R.id.group_providers,false,true);
        super.onCreateContextMenu(menu, v, menuInfo);
    }
*/

    private void connect() {
        appendToLog("Starting");
        if (m_ampManager == null) {
            // m_provider = new AndroidUsbAmpProvider(this);
            IAmpProvider provider = new SimulatorAmpProvider(
                m_loggingAgent,
                SimulatorAmpProvider.SimulationMode.NO_DEVICE
            );
            m_ampManager = new AmpManager(provider);
        }
        m_ampManager.getPresets().acceptVisitor(this);

        /*
        for(int i=0; i<commandHexStrings.length; ++i)
        {
            byte[] requestBytes = ByteArrayTranslator.hexToBytes(commandHexStrings[i]);
            String requestHexString = ByteArrayTranslator.bytesToHex(requestBytes);
            sb.append("R1: " + commandHexStrings[i] + "\n");
            sb.append("R2: " + requestHexString + "\n");
        }
        */

        appendToLog("Started");
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

    @SuppressLint("DefaultLocale")
    @Override
    public void onUsbHidDeviceConnected(UsbHidDevice device) {
        String[] commandHexStrings = new String[]{
                "35:09:08:00:8a:07:04:08:00:10:00",
                "35:07:08:00:b2:06:02:08:01",
                // Next 60 + 1 commands get
                // the JSON descriptions of the 60 stored
                // and 1 active presets
                // We can't handle these until we have support
                // for multi-frame responses
                //"35:07:08:00:ca:06:02:08:01",
                //"35:07:08:00:ca:06:02:08:02",
                //..
                "35:07:08:00:f2:03:02:08:01",
                "35:07:08:00:d2:06:02:08:01",
                "35:07:08:00:e2:06:02:08:01",
                "35:07:08:00:d2:0c:02:08:01",
                "35:09:08:00:8a:07:04:08:01:10:00",
                /*
                "35:07:08:00:8a:02:02:08:02",
                "35:07:08:00:8a:02:02:08:03",
                "35:07:08:00:8a:02:02:08:04",
                "35:07:08:00:8a:02:02:08:01",
                "35:07:08:00:8a:02:02:08:02",
                 */
        };
        appendToLog("Device HID connection succeeded");
/*
        int i=0;
        try {
            for (i = 0; i < commandHexStrings.length; ++i) {
                m_ampManager.sendCommand(commandHexStrings[i], m_sbLog);
            }
            PresetInfo pi = m_ampManager.getPresetInfo(null);
            pi.acceptVisitor(this);
        }
        catch(Exception e) {
            appendToLog(String.format(
                    "Exception caught processing command %d: %s",
                    i, e.toString()
            ));
        }
    */
    }

    @Override
    public void onUsbHidDeviceConnectFailed(UsbHidDevice device) {
        appendToLog("Failed to connect to physical amp, trying simulator...");
        m_ampManager = null;
        IAmpProvider provider = new SimulatorAmpProvider(null, SimulatorAmpProvider.SimulationMode.NO_DEVICE);
        m_ampManager = new AmpManager(provider);
        appendToLog(null);
    }

    @Override
    public void addMenuProvider(@NonNull MenuProvider provider, @NonNull LifecycleOwner owner, @NonNull Lifecycle.State state) {

    }
}
