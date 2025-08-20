package net.heretical_camelid.maneline.android_app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;

public class UsbBroadcastReceiver extends BroadcastReceiver {
    public UsbBroadcastReceiver() { }

    public void onReceive(Context context, Intent intent) {
        MainActivity mainActivity = MainActivity.getInstance();
        if ( mainActivity != null ) {
            // This notification can arrive during or even after shutdown
            // TODO: We should deregister rather than just ignoring it
            return;
        }
        String action = intent.getAction();
        if (
            action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED) ||
            action.equals(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        ) {
            MainActivity.appendToLogStatic("Device attached");
            mainActivity.onUsbDeviceAttached();
        } else if (
            action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED) ||
            action.equals(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        ) {
            MainActivity.appendToLogStatic("Device detached");
        } else if (action.equals(DeviceTransportUsbHid.ACTION_USB_PERMISSION)) {
            if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,true)) {
                MainActivity.appendToLogStatic("USB access permission granted");
            } else {
                MainActivity.appendToLogStatic("USB access permission denied");
            }
        } else {
            MainActivity.appendToLogStatic("BroadcastReceiver received unexpected action: " +action);
        }
    }
}
