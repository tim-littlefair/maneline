package net.heretical_camelid.fhau.android_app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;

public class UsbBroadcastReceiver extends BroadcastReceiver {
    AndroidUsbAmpProvider m_provider;
    public UsbBroadcastReceiver() {
        m_provider = null;
    }

    void setProvider(AndroidUsbAmpProvider provider) {
        m_provider = provider;
    }

    public void onReceive(Context context, Intent intent) {
        assert m_provider!=null;
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
            if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)) {
                MainActivity.appendToLog("USB access permission granted");
                MainActivity mainActivity = (MainActivity) context;
                assert (mainActivity) != null;
                m_provider.usbAccessPermissionGranted();
            } else {
                m_provider.usbAccessPermissionDenied();
            }
        }
    }
}
