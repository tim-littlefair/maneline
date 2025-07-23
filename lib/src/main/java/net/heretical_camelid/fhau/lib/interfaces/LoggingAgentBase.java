package net.heretical_camelid.fhau.lib.interfaces;

public abstract class LoggingAgentBase implements ILoggingAgent {
    @Override
    public void appendToLog(String messageToAppend) {
        this.appendToLog(messageToAppend,null);
    }
}
