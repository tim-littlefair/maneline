package net.heretical_camelid.fhau.android_app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;

class UsbBroadcastReceiver extends BroadcastReceiver {
    public UsbBroadcastReceiver() { }

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (
            UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) ||
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)
        ) {
            MainActivity.appendToLog("Device attached");
        } else if (
                   UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) ||
                       UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)
        ) {
            MainActivity.appendToLog("Device detached");
        } else if (UsbManager.EXTRA_PERMISSION_GRANTED.equals(action)) {
            MainActivity.appendToLog("Device permission granted or denied");
        }
    }
}
