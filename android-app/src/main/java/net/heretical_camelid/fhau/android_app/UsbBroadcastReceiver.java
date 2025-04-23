package net.heretical_camelid.fhau.android_app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;

public class UsbBroadcastReceiver extends BroadcastReceiver {
    public UsbBroadcastReceiver() { }

    public void onReceive(Context context, Intent intent) {
        MainActivity mainActivity = MainActivity.getInstance();
        assert (mainActivity) != null;
        String action = intent.getAction();
        if (
            action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED) ||
            action.equals(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        ) {
            mainActivity.appendToLog("Device attached");
        } else if (
            action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED) ||
            action.equals(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        ) {
            mainActivity.appendToLog("Device detached");
        } else if (action.equals(DeviceTransportUsbHid.ACTION_USB_PERMISSION)) {
            if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,true)) {
                mainActivity.appendToLog("USB access permission granted");
                mainActivity.m_provider.m_deviceTransportUsbHid.attemptUsbHidConnection();
            } else {
                mainActivity.appendToLog("USB access permission denied - but trying anyway");
                mainActivity.m_provider.m_deviceTransportUsbHid.attemptUsbHidConnection();
            }
        } else {
            mainActivity.appendToLog("BroadcastReceiver received unexpected action: " +action);
        }
    }
}
