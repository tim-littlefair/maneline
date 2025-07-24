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
import net.heretical_camelid.fhau.lib.interfaces.LoggingAgentBase;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class WebModeLoggingAgent extends LoggingAgentBase {
    private static final Logger m_logger = LoggerFactory.getLogger(WebModeLoggingAgent.class.getName());

    private static String m_sessionName = null;
    private static String m_transactionName = null;

    private static PrintStream m_sessionLog;

    static WebModeLoggingAgent s_instance = null;

    public WebModeLoggingAgent() {
        m_sessionName = null;
        m_transactionName = null;

        // Hopefully this is only instantiated once
        assert s_instance == null;
        s_instance = this;
    }
    @Override
    public void clearLog() {

    }

    @Override
    public void appendToLog(String messageToAppend, Object o) {
        assert messageToAppend != null;
        final String messageToAppendWithObject;
        if(o!=null) {
            messageToAppendWithObject = String.format(
                "%s object=%s", messageToAppend, o
            );
        } else {
            messageToAppendWithObject = messageToAppend;
        }
        if(m_sessionLog==null) {
            System.out.println(messageToAppendWithObject);
        } else if(m_transactionName != null) {
            m_logger.info(messageToAppend, o);
            m_sessionLog.println(messageToAppendWithObject);
        } else {
            m_sessionLog.println(messageToAppendWithObject);
        }
    }

    @Override
    public void setSessionName(String sessionName) {
        super.setSessionName(sessionName);
        try {
            m_sessionLog = new PrintStream(new FileOutputStream(
                sessionName + "/session.log"
            ));
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    public void setTransactionName(String transactionName) {
        if(transactionName!=null) {
            m_sessionLog.println(String.format(
                "Logging for transaction %s begins", transactionName
            ));
            m_transactionName = transactionName;
            LogbackRollingPolicy_SingleMessagePerFile.setFilenamePattern(
                String.format("%s/%s-%%03d.%s",m_sessionName, m_transactionName,"json")
            );
        } else {
            m_sessionLog.println(String.format(
                "Logging for transaction %s has ended", transactionName
            ));
            m_transactionName = null;
        }
    }
}
