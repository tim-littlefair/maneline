package net.heretical_camelid.fhau.lib;

/**
 * The class below is available as a default implementation
 * of ILoggingAgent for clients.
 * Within the current package, it is used for clients which pass a null
 * as the loggingAgent parameter to the AmpManager constructor.
 */
public class DefaultLoggingAgent implements ILoggingAgent {
    public DefaultLoggingAgent() { }
    @Override
    public void clearLog() { }
    @Override
    public void setLevel(int loggingLevel) { }
    @Override
    public void appendToLog(int loggingLevel, String messageToAppend) {
        System.out.println(messageToAppend);
    }
}
