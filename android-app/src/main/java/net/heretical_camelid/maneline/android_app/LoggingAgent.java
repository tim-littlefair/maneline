package net.heretical_camelid.maneline.android_app;

import android.widget.TextView;

import net.heretical_camelid.maneline.lib.interfaces.ILoggingAgent;
import net.heretical_camelid.maneline.lib.interfaces.LoggingAgentBase;

public class LoggingAgent
    extends LoggingAgentBase
    implements ILoggingAgent {

    StringBuilder m_sbLog;
    final TextView m_tvLog;
    LoggingAgent(TextView tvLog) {
        m_tvLog = tvLog;
        m_sbLog = new StringBuilder();
    }
    public void clearLog() {
        m_sbLog = new StringBuilder();
    }

    @Override
    public void appendToLog(String messageToAppend, Object extraObject) {
        if(extraObject!=null) {
            assert messageToAppend!=null;
            messageToAppend += extraObject.toString();
        }

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

