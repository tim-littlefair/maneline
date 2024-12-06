package net.heretical_camelid.fhau.lib;

import android.content.Context;

public interface IProvider {
    void connect(Context context, String[] commandHexStrings, StringBuilder sb);
}
