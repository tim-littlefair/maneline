package net.heretical_camelid.fhau.android_app;

import android.widget.TextView;

import net.heretical_camelid.fhau.lib.interfaces.ILoggingAgent;

public class LoggingAgent implements ILoggingAgent {

    StringBuilder m_sbLog;
    final TextView m_tvLog;
    LoggingAgent(TextView tvLog) {
        m_tvLog = tvLog;
        m_sbLog = new StringBuilder();
    }
    public void clearLog() { }
    public void setLevel(int loggingLevel) { }
    public void appendToLog(int loggingLevel, String messageToAppend) {
        if(messageToAppend!=null) {
            m_sbLog.append(messageToAppend + "\n");
        } else {
            // A null message can be appended to trigger re-display
            // of the content of m_sbLog if it has been passed to
            // another class and may have been appended.
        }
        m_tvLog.setText(m_sbLog.toString());
    }
}

