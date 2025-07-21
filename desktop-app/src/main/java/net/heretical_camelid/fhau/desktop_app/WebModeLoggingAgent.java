package net.heretical_camelid.fhau.desktop_app;

/*
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.simple.SimpleLogger;
*/

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.logstash.logback.argument.StructuredArguments.kv;

import net.heretical_camelid.fhau.lib.interfaces.ILoggingAgent;

public class WebModeLoggingAgent implements ILoggingAgent {
    private static final Logger m_logger = LoggerFactory.getLogger(WebModeLoggingAgent.class.getName());

    public WebModeLoggingAgent() {
    }
    @Override
    public void clearLog() {

    }

    @Override
    public void setLevel(int loggingLevel) {

    }

    @Override
    public void appendToLog(int loggingLevel, String messageToAppend) {
        m_logger.info(messageToAppend, kv("abc","def"));
    }

    static public void setLogFile(String logFilePathPrefix) {
        ThreadContext.put("ID",logFilePathPrefix);
    }
}
