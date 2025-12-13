package net.heretical_camelid.maneline.lib.interfaces;

import java.util.HashMap;
import java.util.Map;

/**
 * The interface below is intended to define an interface for
 * logging.
 */
public interface ILoggingAgent {
    void setSessionName(String sessionName);
    void setTransactionName(String transactionName);
    void clearLog();
    void appendToLog(String messageToAppend, Map<String,String> extraAttributes);
    void appendToLog(String messageToAppend);


    Object getTransactionName();

    default void logException(Throwable e, StringBuilder userMessages) {
        HashMap<String,String> exceptionDetails = new HashMap<String,String>();
        exceptionDetails.put("exceptionType", e.getClass().getCanonicalName());
        exceptionDetails.put("exceptionMessage", e.getMessage());
        // Try to show the line of the most recent maneline function
        // executed.
        for(StackTraceElement ste: e.getStackTrace()) {
            if(ste.getClassName().startsWith("net.heretical_camelid.maneline")) {
                exceptionDetails.put("thrownFrom", String.format(
                    "%s:%d", ste.getFileName(), ste.getLineNumber()
                ));
                break;
            }
        }
        appendToLog(
            "Exception caught in AudioRecorder.findPCM(...)",
            exceptionDetails
        );
        if(userMessages!=null){
            userMessages.append(e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("No tests for ILoggingAgent");
    }
}

