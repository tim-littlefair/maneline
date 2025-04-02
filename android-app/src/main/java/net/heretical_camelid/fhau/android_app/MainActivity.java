package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import net.heretical_camelid.fhau.lib.*;

import java.util.*;

import static net.heretical_camelid.fhau.lib.IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_SUCCEEDED;

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
    implements PresetInfo.IVisitor, AdapterView.OnItemSelectedListener {
    static LoggingAgent s_loggingAgent;
    private AndroidUsbAmpProvider m_provider;
    AmpManager m_ampManager = null;
    PresetSuiteRegistry m_presetSuiteRegistry = null;
    Button m_btnConnectionStatus;

    public MainActivity() {
    }

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
                if(itemId == R.id.action_provider_sim_nodev) {
                    m_ampManager.setProvider(new SimulatorAmpProvider(
                        s_loggingAgent,
                        SimulatorAmpProvider.SimulationMode.NO_DEVICE
                    ));
                } else if(itemId == R.id.action_provider_sim_lt40s) {
                    m_ampManager.setProvider(new SimulatorAmpProvider(
                        s_loggingAgent,
                        SimulatorAmpProvider.SimulationMode.NO_DEVICE
                    ));
                } else if(itemId == R.id.action_provider_sim_mmp) {
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
        setSupportActionBar(findViewById(R.id.toolbar_fhau));

        TextView tvLog = (TextView) findViewById(R.id.tv_log);
        s_loggingAgent = new LoggingAgent(tvLog);
        tvLog.setText("");
        if(BuildConfig.DEBUG == true) {
            appendToLog("FHAU debug variant built at " + BuildConfig.BUILD_TIME);
        } else {
            appendToLog("FHAU version " + BuildConfig.VERSION_NAME);
        }

        m_ampManager = new AmpManager(s_loggingAgent);
        m_provider = new AndroidUsbAmpProvider(this);
        m_ampManager.setProvider(m_provider);

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
        connect();
    }

    @Override
    protected void onNewIntent(Intent theIntent) {
        super.onNewIntent(theIntent);
        appendToLog(theIntent.toString());
    }

    void populatePresetSuiteDropdown() {
        AndroidUsbAmpProvider provider = (AndroidUsbAmpProvider)(m_ampManager.m_provider);
        assert provider!=null;
        FenderJsonPresetRegistry registry = (FenderJsonPresetRegistry)(provider.m_presetRegistry);
        assert registry!=null;
        m_presetSuiteRegistry = new PresetSuiteRegistry(registry);
        ArrayList<PresetSuiteRegistry.PresetSuiteEntry> presetSuites =
            m_presetSuiteRegistry.buildPresetSuites(9,3,5)
        ;
        int itemLayoutId = R.layout.preset_suite_dropdown_item;

        ArrayList<String> suiteNames = new ArrayList<>();
        for(PresetSuiteRegistry.PresetSuiteEntry pse: presetSuites) {
            suiteNames.add(pse.name());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, itemLayoutId, suiteNames.toArray(new String[]{ }
        ));

        // Bind the items
        Spinner presetSuiteDropdown = (Spinner) findViewById(R.id.dropdown_preset_suites);
        presetSuiteDropdown.setOnItemSelectedListener(this);
        adapter.setDropDownViewResource(itemLayoutId);
        presetSuiteDropdown.setAdapter(adapter);
    }

    void connect() {
        IAmpProvider.ProviderState_e cxnStatus = m_provider.attemptConnection();
        if(cxnStatus == PROVIDER_DEVICE_CONNECTION_SUCCEEDED) {
            m_provider.getFirmwareVersionAndPresets();
            populatePresetSuiteDropdown();
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
                    appendToLog("click for preset " + slotId);
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


    void requestFileStoragePermission() {
        // TODO:
        // Complete this logic to enable logs and preset JSON files
        // to be exposed when the Android device is connected to a computer
        // as a USB drive.
        // The purpose of this permission in terms of Play Store policy
        // compliance will be to enable backup and sharing of presets
        // and suites of presets
        /*
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
         */
    }

    private void setupPresetButtonsForSuite(String suiteName, HashMap<Integer, FenderJsonPresetRegistry.Record> suitePresetRecords) {
        clearPresetButtons();
        appendToLog("Preset suite '" + suiteName + "' selected");
        ArrayList<Integer> slotIndices = new ArrayList<>(suitePresetRecords.keySet());
        slotIndices.sort(null);
        for(int i=0; i<slotIndices.size(); ++i) {
            int slotIndex = slotIndices.get(i);
            FenderJsonPresetRegistry.Record presetRecord = suitePresetRecords.get(slotIndex);
            setPresetButton(
                i+1, slotIndex,
                PresetSuiteRegistry.buttonLabel(slotIndex, presetRecord.displayName())
            );
            appendToLog(
                presetRecord.displayName().replace("( )+"," ") +
                    ": " +  presetRecord.effects()
            );
        }
    }
/*
    void setupPresetButtonsForSuite(PresetSuiteRegistry.PresetSuiteEntry selectedSuite, ArrayList<FenderJsonPresetRegistry.Record> suitePresetRecords, PresetSuiteRegistry presetSuiteRegistry) {
        clearPresetButtons();
        appendToLog("Preset suite '" + selectedSuite.name() + "' selected");
        for(int i = 0; i< suitePresetRecords.size(); ++i) {
            FenderJsonPresetRegistry.Record presetRecord = suitePresetRecords.get(i);
            setPresetButton(
                i+1, i+1, PresetSuiteRegistry.buttonLabel(i+1, presetRecord.displayName())
            );
            appendToLog(
                presetRecord.displayName().replace("( )+"," ") +
                ": " +  presetRecord.effects()
            );
        }
    }
 */


    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        clearPresetButtons();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        /*
        assert m_presetSuiteRegistry.m_presetSuites!=null;
        PresetSuiteRegistry.PresetSuiteEntry selectedSuite = m_presetSuiteRegistry.m_presetSuites.get(position);
        ArrayList<FenderJsonPresetRegistry.Record> suitePresetRecords = selectedSuite.second;
         */
        String suiteName = m_presetSuiteRegistry.nameAt(position);
        HashMap<Integer,FenderJsonPresetRegistry.Record> suitePresetRecords = m_presetSuiteRegistry.recordsAt(position);
        setupPresetButtonsForSuite(suiteName, suitePresetRecords);
    }

}

