package net.heretical_camelid.fhau.android_app;

import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import net.heretical_camelid.fhau.lib.AndroidUsbProvider;
import net.heretical_camelid.fhau.lib.AndroidSimulatorProvider;
import net.heretical_camelid.fhau.lib.IProvider;

public class MainActivity extends AppCompatActivity {

    IProvider m_provider = null;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (m_provider == null) {
           m_provider = new AndroidSimulatorProvider();
        }

        setSupportActionBar(findViewById(R.id.toolbar_fhau));

        findViewById(R.id.button_test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                test();
            }
        });
    }

    private void test() {
        StringBuilder sb = new StringBuilder();

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
                /*
                "35:07:08:00:f2:03:02:08:01",
                "35:07:08:00:d2:06:02:08:01",
                "35:07:08:00:e2:06:02:08:01",
                "35:07:08:00:d2:0c:02:08:01",
                "35:09:08:00:8a:07:04:08:01:10:00",
                "35:07:08:00:8a:02:02:08:02",
                "35:07:08:00:8a:02:02:08:03",
                "35:07:08:00:8a:02:02:08:04",
                "35:07:08:00:8a:02:02:08:01",
                "35:07:08:00:8a:02:02:08:02",
                 */
        };
        /*
        for(int i=0; i<commandHexStrings.length; ++i)
        {
            byte[] requestBytes = ByteArrayTranslator.hexToBytes(commandHexStrings[i]);
            String requestHexString = ByteArrayTranslator.bytesToHex(requestBytes);
            sb.append("R1: " + commandHexStrings[i] + "\n");
            sb.append("R2: " + requestHexString + "\n");
        }
        */

        TextView report_tv = (TextView) findViewById(R.id.textview_report);
        sb.append("Starting\n");
        m_provider.connect(this, commandHexStrings, sb);
        report_tv.setText(sb.toString());

    }
}
