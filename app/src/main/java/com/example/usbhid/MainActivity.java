package com.example.usbhid;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.benlypan.usbhid.OnUsbHidDeviceListener;
import com.benlypan.usbhid.UsbHidDevice;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                test();
            }
        });
    }

    private void test() {
        StringBuilder sb = new StringBuilder();
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
        sb.append(String.format(
            "Device found: id=%16x sn=%s\n",
            device.getDeviceId(), device.getSerialNumber()
        ));
        report_tv.setText(sb.toString());
        sb.append(String.format(
            "Device found: vid=%04x pid=%04x pname=%s\n",
            device.getUsbDevice().getVendorId(),
            device.getUsbDevice().getProductId(),
            device.getUsbDevice().getProductName()
        ));
        report_tv.setText(sb.toString());
        device.open(this, new OnUsbHidDeviceListener() {
            @Override
            public void onUsbHidDeviceConnected(UsbHidDevice device) {
                sb.append("Device HID connection succeeded\n");
                report_tv.setText(sb.toString());
                /*
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
