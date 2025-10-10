package net.heretical_camelid.maneline.lib;

import net.heretical_camelid.maneline.lib.interfaces.ILoggingAgent;
import net.heretical_camelid.maneline.lib.interfaces.LoggingAgentBase;

import java.util.Map;

/**
 * The class below is available as a default implementation
 * of ILoggingAgent for clients.
 */
public class DefaultLoggingAgent
    extends LoggingAgentBase
    implements ILoggingAgent
    {
    public DefaultLoggingAgent() {
    }
    @Override
    public void clearLog() { }

    @Override
    public void appendToLog(String messageToAppend, Map<String,String> extraAttributes) {
        if(extraAttributes != null) {
            messageToAppend += extraAttributes.toString();
        }
        System.out.println(messageToAppend);
    }
}
