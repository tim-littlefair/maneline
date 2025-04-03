package net.heretical_camelid.fhau.lib;

import net.heretical_camelid.fhau.lib.interfaces.ILoggingAgent;

/**
 * The class below is available as a default implementation
 * of ILoggingAgent for clients.
 * Within the current package, it is used for clients which pass a null
 * as the loggingAgent parameter to the AmpManager constructor.
 */
public class DefaultLoggingAgent implements ILoggingAgent {
    int m_maxVisibleLoggingLevel;
    public DefaultLoggingAgent(int initialMVLL) {
        m_maxVisibleLoggingLevel = initialMVLL;
    }
    @Override
    public void clearLog() { }
    @Override
    public void setLevel(int loggingLevel) {
        m_maxVisibleLoggingLevel = loggingLevel;
    }
    @Override
    public void appendToLog(int loggingLevel, String messageToAppend) {
        if(loggingLevel<=m_maxVisibleLoggingLevel) {
            System.out.println(String.format("%1d %s", loggingLevel, messageToAppend));
        }
    }

    public static void main(String[] args) {
        System.out.println("Tests for DefaultLoggingAgent");
        DefaultLoggingAgent dla = new DefaultLoggingAgent(2);
        dla.appendToLog(4,"Should not be visible");
        dla.appendToLog(0, "Should be first visible message");
        dla.appendToLog(1,"Should be second visible message");
        dla.appendToLog(2,"Should be third and last visible message");
        dla.appendToLog(3,"Should not be visible");

    }
}
