package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.benlypan.usbhid.OnUsbHidDeviceListener;
import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.PresetInfo;
import net.heretical_camelid.fhau.lib.PresetRecord;
import net.heretical_camelid.fhau.lib.SimulatorAmpProvider;
import net.heretical_camelid.fhau.lib.IAmpProvider;

public class MainActivity
        extends AppCompatActivity
        implements PresetInfo.IVisitor, OnUsbHidDeviceListener
{

    IAmpProvider m_provider = null;
    Button m_btnConnectionStatus;
    StringBuilder m_sbLog;
    TextView m_tvLog;
    void appendToLog(String message) {
        if(message!=null) {
            m_sbLog.append(message + "\n");
        } else {
            // A null message can be appended to trigger re-display
            // of the content of m_sbLog if it has been passed to
            // another class and may have been appended.
        }
        m_tvLog.setText(m_sbLog.toString());
    }

    int m_lastPresetInUse = 0;
    final static int MAX_PRESET = 3;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        /*
        if (itemId==R.id.action_connect) {
            m_btnConnectionStatus.callOnClick();
        } else if (itemId==R.id.action_disconnect) {
            Toast.makeText(this, "TODO: Implement disconnect", Toast.LENGTH_LONG).show();
        } else
        */
        if (itemId==R.id.action_settings) {
            Toast.makeText(this, "TODO: Implement settings dialog", Toast.LENGTH_LONG).show();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        m_provider = null;

        m_sbLog = new StringBuilder();
        m_tvLog = (TextView) findViewById(R.id.tv_log);
        appendToLog("Starting up");

        setSupportActionBar(findViewById(R.id.toolbar_fhau));

        m_btnConnectionStatus = findViewById(R.id.btn_cxn_status);
        m_btnConnectionStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect();
            }
        });
    }

    private void connect() {
        if (m_provider == null) {
            m_provider = new AndroidUsbAmpProvider(this);
        }

        /*
        for(int i=0; i<commandHexStrings.length; ++i)
        {
            byte[] requestBytes = ByteArrayTranslator.hexToBytes(commandHexStrings[i]);
            String requestHexString = ByteArrayTranslator.bytesToHex(requestBytes);
            sb.append("R1: " + commandHexStrings[i] + "\n");
            sb.append("R2: " + requestHexString + "\n");
        }
        */

        appendToLog("Starting");
        boolean cxnSucceeded = m_provider.connect(m_sbLog);
        appendToLog("Started");
    }

    private void switchPreset(int whichSlot) {
        String commandHexString = String.format(
                "35:07:08:00:8a:02:02:08:%02x",
                whichSlot
        );
        appendToLog(String.format("Requesting switch to preset %02d",whichSlot));
        m_provider.sendCommand(commandHexString, m_sbLog);
        appendToLog("Preset switch command sent");
    }

    @Override
    public void visit(PresetRecord pr) {
        if (m_lastPresetInUse == MAX_PRESET) {
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
        presetButton.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchPreset(pr.m_slotNumber);
            }
        }));
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
        int i=0;
        try {
            for (i = 0; i < commandHexStrings.length; ++i) {
                m_provider.sendCommand(commandHexStrings[i], m_sbLog);
            }
            PresetInfo pi = m_provider.getPresetInfo(null);
            pi.acceptVisitor(this);
        }
        catch(Exception e) {
            appendToLog(String.format(
                    "Exception caught processing command %d: %s",
                    i, e.toString()
            ));
        }
    }

    @Override
    public void onUsbHidDeviceConnectFailed(UsbHidDevice device) {
        appendToLog("Failed to connect to physical amp, trying simulator...");
        m_provider = new SimulatorAmpProvider(SimulatorAmpProvider.SimulationMode.NO_DEVICE);
        m_provider.connect(m_sbLog);
        appendToLog(null);
    }
}
