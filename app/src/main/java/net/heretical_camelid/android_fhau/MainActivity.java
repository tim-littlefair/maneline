package net.heretical_camelid.android_fhau;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.benlypan.usbhid.OnUsbHidDeviceListener;
import com.benlypan.usbhid.UsbHidDevice;

// It is a mystery why the resource class is still being
// generated under com.example.usbhid.R rather than
// net.heretical_camelid.android_fhau.R.
// For the moment this still seems to work.
import com.example.usbhid.R;

import net.heretical_camelid.fhau.lib.ByteArrayTranslator;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        report_tv.setText(sb.toString());
        // Upstream UsbHid project searched for this VID/PID
        // UsbHidDevice device = UsbHidDevice.factory(this, 0x0680, 0x0180);
        // This fork searches for any device associated with the Fender VID
        UsbHidDevice device = UsbHidDevice.factory(this, 0x0, 0x0);
        if (device == null) {
            sb.append("No device found\n");
            report_tv.setText(sb.toString());
            return;
        }
        UsbDevice usbDevice = device.getUsbDevice();
        sb.append(String.format(
            "Device found: id=%d sn=%s vid=%04x pid=%04x pname=%s\n",
            device.getDeviceId(),
            device.getSerialNumber(),
            usbDevice.getVendorId(),
            usbDevice.getProductId(),
            usbDevice.getProductName()
        ));
        report_tv.setText(sb.toString());
        device.open(this, new OnUsbHidDeviceListener() {
            @Override
            public void onUsbHidDeviceConnected(UsbHidDevice device) {
                sb.append("Device HID connection succeeded\n");
                report_tv.setText(sb.toString());

                for(int i=0; i<commandHexStrings.length; ++i)
                {
                    device.write(ByteArrayTranslator.hexToBytes(commandHexStrings[i]));
                    byte[] responseBytes  = device.read(64);
                    sb.append("Sent " + commandHexStrings[i] + "\n");
                    sb.append("Received " + ByteArrayTranslator.bytesToHex(responseBytes) + "\n");
                    report_tv.setText(sb.toString());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        sb.append("sleep interrupted\n");
                    }
                }

                /*
                 * The upstream example was intended for a specific
                 * device, so presumably sending this message would
                 * do no harm.
                 * As this fork will connect to the first device found,
                 * it is safer not to send anything
                byte[] sendBuffer = new byte[64];
                sendBuffer[0] = 0x01;
                sendBuffer[1] = 0x03;
                device.write(sendBuffer);
                byte[] result = device.read(64);
                 */
            }

            @Override
            public void onUsbHidDeviceConnectFailed(UsbHidDevice device) {
                sb.append("Device HID connection failed\n");
                report_tv.setText(sb.toString());
            }
        });
    }
}
