package net.heretical_camelid.fhau.android_app;

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

import net.heretical_camelid.fhau.lib.interfaces.IAmpProvider;
import net.heretical_camelid.fhau.lib.registries.FenderJsonPresetRegistry;
import net.heretical_camelid.fhau.lib.registries.PresetSuiteRegistry;

import java.util.*;

import static net.heretical_camelid.fhau.lib.interfaces.IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_SUCCEEDED;

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
        implements AdapterView.OnItemSelectedListener {
    static LoggingAgent s_loggingAgent;
    AndroidUsbAmpProvider m_provider;
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
                // TODO: Do something here
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

        m_provider = new AndroidUsbAmpProvider(this);

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
        assert m_provider!=null;
        /*
        FenderJsonPresetRegistry registry = (FenderJsonPresetRegistry)(m_provider.m_presetRegistry);
        assert registry!=null;
        PresetSuiteRegistry m_presetSuiteRegistry = new PresetSuiteRegistry(registry);
        ArrayList<PresetSuiteRegistry.PresetSuiteEntry> presetSuites =
            m_presetSuiteRegistry.buildPresetSuites(9,3,5)
        ;
         */
        ArrayList<PresetSuiteRegistry.PresetSuiteEntry> presetSuites = m_provider.buildAmpBasedPresetSuites(
            9,5,3
        );
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
            appendToLog("Connection succeeded");
            m_provider.getFirmwareVersionAndPresets();
            populatePresetSuiteDropdown();
            appendToLog("Presets populated");
        } else {
            appendToLog("cxnStatus=" + cxnStatus);
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
                    m_provider.switchPreset(slotId);
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

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        clearPresetButtons();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        m_provider.switchSuite(position);
    }

    void suiteSelected(
        String suiteName,
        HashMap<Integer,FenderJsonPresetRegistry.Record> suitePresetRecords
    ) {
        /*
        String suiteName = m_presetSuiteRegistry.nameAt(position);
        HashMap<Integer,FenderJsonPresetRegistry.Record> suitePresetRecords = m_presetSuiteRegistry.recordsAt(position);
         */
        setupPresetButtonsForSuite(suiteName, suitePresetRecords);
    }

}

