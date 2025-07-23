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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class WebModeLoggingAgent implements ILoggingAgent {
    private static final Logger m_logger = LoggerFactory.getLogger(WebModeLoggingAgent.class.getName());

    private static String m_sessionName = null;
    private static String m_transactionName = null;

    private static PrintStream m_sessionLog;

    public WebModeLoggingAgent() {
        m_sessionName = null;
        m_transactionName = null;
    }
    @Override
    public void clearLog() {

    }

    @Override
    public void setLevel(int loggingLevel) {

    }

    @Override
    public void appendToLog(int loggingLevel, String messageToAppend) {
        if(m_transactionName != null) {
            m_sessionLog.println(messageToAppend);
            m_logger.info(messageToAppend, kv("abc", "def"));
        } else {
            assert m_sessionLog != null: "Session log has not been created";
            m_sessionLog.println(messageToAppend);
        }
    }

    static public void setSessionName(String sessionName) {
        assert m_sessionName == null: "Session name should only be set once";
        m_sessionName = sessionName;
        try {
            m_sessionLog = new PrintStream(new FileOutputStream(
                m_sessionName + "/session.log"
            ));
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    static public void setTransactionName(String transactionName) {
        assert m_sessionName != null: "Transaction name should not be set before session name";
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
