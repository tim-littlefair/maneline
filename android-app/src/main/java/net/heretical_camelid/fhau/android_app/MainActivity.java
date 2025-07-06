package net.heretical_camelid.fhau.android_app;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
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
import net.heretical_camelid.fhau.lib.registries.PresetRecord;
import net.heretical_camelid.fhau.lib.registries.SuiteRecord;
import net.heretical_camelid.fhau.lib.registries.SuiteRegistry;

import java.util.*;

import static net.heretical_camelid.fhau.android_app.MessageType_e.*;
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

enum MessageType_e {
    MESSAGE_PROVIDER_CONNECTION_FAILED,
    MESSAGE_PROVIDER_PERMISSION_GRANTED,
    MESSAGE_PROVIDER_CONNECTED,
    MESSAGE_PROVIDER_STARTUP_COMPLETED,
    MESSAGE_PRESETS_DOWNLOADED,
    MESSAGE_PRESET_SELECTED,
    MESSAGE_PROVIDER_CONNECTION_BROKEN,
    MESSAGE_APPEND_TO_LOG,
}

public class MainActivity
        extends AppCompatActivity
        implements AdapterView.OnItemSelectedListener {

    // The following constants are used as keys when messages are sent to m_providerHandler
    public static final String MESSAGE_LOG_APPEND_STRING = "MESSAGE_LOG_APPEND_STRING";
    public static final String MESSAGE_SLOT_INDEX = "MESSAGE_SLOT_INDEX";
    public static final String MESSAGE_PRESET_NAME = "MESSAGE_PRESET_NAME";
    public static final String MESSAGE_PRESET_EFFECTS = "MESSAGE_PRESET_EFFECTS";

    LoggingAgent m_loggingAgent;
    AndroidUsbAmpProvider m_provider;
    Thread m_providerThread;
    Handler m_providerHandler;

    TextView m_tvLog;
    Button m_btnConnectionStatus;

    static MainActivity s_instance = null;
    public static MainActivity getInstance() {
        return s_instance;
    }

    void appendToLog(String message) {
        long currentThreadId = Thread.currentThread().getId();
        if (
            m_loggingAgent!=null &&
            currentThreadId==this.getMainLooper().getThread().getId()
        ) {
            System.out.println(message);
            m_loggingAgent.appendToLog(0,message);
        } else if (m_providerHandler!=null) {
            // This message will be displayed when the
            // handler processes it
            // The following line can be uncommented if we
            // want to see when it is despatched as well
            // as when it is handled.
            // System.out.println("T " + message);
            Message logMessage = new Message();
            logMessage.what = MESSAGE_APPEND_TO_LOG.ordinal();
            Bundle messageBundle = new Bundle();
            messageBundle.putString(MESSAGE_LOG_APPEND_STRING, message);
            logMessage.setData(messageBundle);
            m_providerHandler.sendMessage(logMessage);
        } else {
            // Message is from a non-main thread but the
            // handler is not yet up, so it can be
            // displayed on standard error but it is not
            // thread safe to send it to the logging agent
            System.out.println("X " + message);
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
        assert s_instance == null;
        s_instance=this;
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar_fhau));

        m_tvLog = (TextView) findViewById(R.id.tv_log);
        m_loggingAgent = new LoggingAgent(m_tvLog);
        m_tvLog.setText("");

        if(BuildConfig.DEBUG == true) {
            appendToLog("FHAU debug variant built at " + BuildConfig.BUILD_TIME);
        } else {
            appendToLog("FHAU version " + BuildConfig.VERSION_NAME);
        }

        m_provider = new AndroidUsbAmpProvider(this);
        m_providerThread = null;
        m_providerHandler = new Handler();

        m_btnConnectionStatus = findViewById(R.id.btn_cxn_status);
        m_btnConnectionStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_loggingAgent.clearLog();
                m_btnConnectionStatus.setText("Connecting");
                connect();
            }
        });

        requestFileStoragePermission();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(m_providerThread ==null) {
            connect();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        assert s_instance != null;
        s_instance = null;
    }

    @Override
    protected void onNewIntent(Intent theIntent) {
        super.onNewIntent(theIntent);
        appendToLog(theIntent.toString());
    }

    void populatePresetSuiteDropdown() {
        assert m_provider!=null;
        PresetSuiteManager psm = new PresetSuiteManager(this);
        ArrayList<SuiteRecord> presetSuites = psm.processDay0Suites(
            m_provider
        );

        ArrayList<String> suiteNames = new ArrayList<>();
        for(SuiteRecord pse: presetSuites) {
            suiteNames.add(pse.name());
        }

        int itemLayoutId = R.layout.preset_suite_dropdown_item;
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
        Runnable providerRunnable = new Runnable() {
            @Override
            public void run() {
                IAmpProvider.ProviderState_e cxnStatus = m_provider.attemptConnection();
                onConnectAttemptOutcome(cxnStatus);
            }
        };
        if (m_providerThread==null || !m_providerThread.isAlive()) {
            appendToLog("Creating new provider thread");
            m_providerThread = new Thread(providerRunnable);
            m_providerThread.start();
        } else {
            appendToLog("Existing provider thread is still alive");
        }
    }

    void onConnectAttemptOutcome(IAmpProvider.ProviderState_e cxnStatus) {
        if(cxnStatus == PROVIDER_DEVICE_CONNECTION_SUCCEEDED) {
            appendToLog("Connected to amplifier - retrieving firmware version and presets");
            m_providerHandler.sendEmptyMessage(MESSAGE_PROVIDER_CONNECTED.ordinal());
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
            if(presetName==null) {
                presetName = "NOT\nIN\nUSE";
            }
            presetButton.setClickable(false);
            presetButton.setOnClickListener(null);
            presetButton.setEnabled(false);
            buttonColor = R.color.fhauGrey;
            buttonAlpha = 0.5F;
        } else {
            if(presetName==null) {
                presetName="NULL";
            }
            presetButton.setClickable(true);
            presetButton.setOnClickListener((new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    m_loggingAgent.clearLog();
                    appendToLog("preset " + slotId + " requested");
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
    }

    private void setupPresetButtonsForSuite(String suiteName, Map<Integer, PresetRecord> suitePresetRecords) {
        clearPresetButtons();
        appendToLog("Preset suite '" + suiteName + "' selected");
        ArrayList<Integer> slotIndices = new ArrayList<>(suitePresetRecords.keySet());
        slotIndices.sort(null);
        for(int i=0; i<slotIndices.size(); ++i) {
            int slotIndex = slotIndices.get(i);
            PresetRecord presetRecord = suitePresetRecords.get(slotIndex);
            setPresetButton(
                i+1, slotIndex,
                SuiteRegistry.buttonLabel(slotIndex, presetRecord.displayName())
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
        Map<Integer, PresetRecord> suitePresetRecords
    ) {
        /*
        String suiteName = m_SuiteRegistry.nameAt(position);
        HashMap<Integer,FenderJsonPresetRegistry.PresetRecord> suitePresetRecords = m_SuiteRegistry.recordsAt(position);
         */
        setupPresetButtonsForSuite(suiteName, suitePresetRecords);
    }

    class Handler extends android.os.Handler {
        public Handler() {
            super(Looper.getMainLooper());
        }

        public void handleMessage(Message m) {
            //assert m_providerThread!=null;
            switch (MessageType_e.values()[m.what]) {
                case MESSAGE_PROVIDER_PERMISSION_GRANTED:
                    m_btnConnectionStatus.setText("Permission granted");
                    m_btnConnectionStatus.callOnClick();
                    break;

                case MESSAGE_PROVIDER_CONNECTED:
                    assert m_providerThread.isAlive()==false;
                    m_btnConnectionStatus.setText("Click to reconnect");
                    m_providerThread = new Thread() {
                        @Override
                        public void run() {
                            m_provider.getFirmwareVersionAndPresets();
                            m_providerHandler.sendEmptyMessage(MESSAGE_PRESETS_DOWNLOADED.ordinal());
                        }
                    };
                    m_providerThread.start();
                    break;

                case MESSAGE_PRESETS_DOWNLOADED:
                    assert m_providerThread.isAlive()==false;
                    m_providerThread = null;
                    appendToLog("Presets retrieved - grouping into suites");
                    populatePresetSuiteDropdown();
                    break;

                case MESSAGE_PRESET_SELECTED:
                    Bundle messageData = m.getData();
                    int slotIndex = messageData.getInt(MESSAGE_SLOT_INDEX);
                    String presetName = messageData.getString(MESSAGE_PRESET_NAME);
                    String effects = messageData.getString(MESSAGE_PRESET_EFFECTS);
                    /*
                    // expand abbreviations in effects for readability
                    effects = effects
                                  // Note that changing ':' to '=' is required
                                  // to prevent every mod: being changed to mo\ndelay:
                                  // Alternatively we could have reordered substitutions
                                  // so delay is done before mod, but changing the
                                  // separator allows us to process the effects in their
                                  // typical order, and feels OK for readability
                                  .replace("s:","\nstomp=")
                                  .replace("m:", "\nmod=")
                                  .replace( "a:", "\namp=")
                                  .replace("d:","\ndelay=")
                                  .replace("r:","\nreverb=")
                                  .strip();
                     */
                    appendToLog("Preset loaded: " + presetName);
                    appendToLog("Effects:\n"+effects+"\n");
                    break;

                case MESSAGE_APPEND_TO_LOG:
                    appendToLog(m.getData().getString(MESSAGE_LOG_APPEND_STRING));
                    break;

                default:
                    appendToLog("Unexpected message received by providerHandler: " + m.what);
            }
        }
    }
}

