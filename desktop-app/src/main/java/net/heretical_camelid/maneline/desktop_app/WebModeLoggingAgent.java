package net.heretical_camelid.maneline.desktop_app;

import net.heretical_camelid.maneline.lib.interfaces.LoggingAgentBase;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;

//import org.slf4j.FileAppender;
// import org.slf4j.Level;
//import org.apache.log4j.FileAppender;
//import org.apache.log4j.Layout;
//import org.apache.log4j.PatternLayout;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.FileAppender;
// import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.Level;
import org.slf4j.spi.DefaultLoggingEventBuilder;
import org.slf4j.spi.LoggingEventBuilder;

/*
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.FileAppender;
*/

class WebModeLogUtility {
    final Logger m_logger;
    FileAppender m_appender;
/*
    final static Layout s_layout = new PatternLayout(
        "%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n"
    );
*/

    WebModeLogUtility(String loggerNamePrefix) {
        m_logger = (Logger) LoggerFactory.getLogger(loggerNamePrefix+"_LOGGER");
        m_appender = (FileAppender) m_logger.getAppender(loggerNamePrefix+"_APPENDER");
    }

    void openNewLogFile(String logFilePath) {
        if(m_appender!=null) {
            m_appender.stop();
        }
        m_appender.setFile(logFilePath);
        m_appender.start();
    }
}

public class WebModeLoggingAgent extends LoggingAgentBase {
    static Map<String,FileHandler> s_appenders = new HashMap<>();
    private static final String SESSION_INFO_LOG_FILE_NAME = "session-info.log";
    private static final String SESSION_DEBUG_LOG_FILE_NAME = "session-debug.log";
    private static final WebModeLogUtility m_sessionLU = new WebModeLogUtility("SESSION");
    private static final WebModeLogUtility m_transactionLU = new WebModeLogUtility("TRANSACTION");

    static WebModeLoggingAgent s_instance = null;

    private int m_txnNumber = 0;
    private int m_txnMessageCounter = 0;

    static void setSessionNameStatic(String sessionName) {
        assert s_instance != null;
        s_instance.setSessionName(sessionName);

        new File(sessionName + "/txns").mkdir();
        m_sessionLU.openNewLogFile(String.format(
            "%s/%s",sessionName,SESSION_INFO_LOG_FILE_NAME
        ));
        //m_sessionLU.m_appender.start();

        // We don't expect debug events to be generated
        // outside transactions, but in case a coding error
        // causes this to happen we define a file to 
        // catch the events.
        //m_transactionLU.m_appender.stop();
        m_transactionLU.openNewLogFile(String.format(
            "%s/%s",sessionName,SESSION_DEBUG_LOG_FILE_NAME
        ));
        //m_transactionLU.m_appender.start();
    }

    static String getSessionNameStatic() {
        assert s_instance != null;
        return s_instance.getSessionName();
    }

    public WebModeLoggingAgent() {
        super();

        // Hopefully this is only instantiated once
        assert s_instance == null;
        s_instance = this;
    }

    @Override
    public void appendToLog(
        String messageToAppend,
        Map<String,String> extraAttributes
    ) {
        assert messageToAppend != null;
        final String messageToAppendWithObject;
        if(extraAttributes!=null) {
            messageToAppendWithObject = String.format(
                "%s object=%s", messageToAppend, extraAttributes
            );
        } else {
            messageToAppendWithObject = messageToAppend;
        }
        LoggingEventBuilder leb;
        if(getTransactionName() != null) {
            leb = new DefaultLoggingEventBuilder(m_transactionLU.m_logger, Level.DEBUG);
            ++m_txnMessageCounter;
        } else {
            leb = new DefaultLoggingEventBuilder(m_sessionLU.m_logger, Level.INFO);
        }
        leb = leb.setMessage(messageToAppend);
        if(extraAttributes!=null) {
            for(String k: extraAttributes.keySet()) {
                leb = leb.addKeyValue(k,extraAttributes.get(k));
            }
        }
        leb.log();
    }

    @Override
    public void setSessionName(String sessionName) {
        super.setSessionName(sessionName);
    }

    @Override
    synchronized public void setTransactionName(String transactionName) {
        assert getSessionName() != null:
            "Session name must be set before setting transaction name"
        ;
        String priorTransactionName = getTransactionName();
        // m_transactionLU.m_appender.stop();
        if(transactionName!=null) {
            assert priorTransactionName == null:
                "Attempted to start a transaction when one was already in progress"
            ;
            m_transactionLU.openNewLogFile(String.format(
                "%s/txns/txn%03d-%s.log",getSessionName(), m_txnNumber, transactionName
            ));
        } else {
            assert priorTransactionName != null:
                "Attempted to end a transaction when one was not in progress"
            ;
            // We don't expect debug events to be generated
            // outside transactions, but in case a coding error
            // causes this to happen we define a file to 
            // catch the events.
            m_transactionLU.openNewLogFile(String.format(
                "%s/%s",getSessionName(),SESSION_DEBUG_LOG_FILE_NAME
            ));
            m_sessionLU.m_logger.info(String.format(
                "%d debug events generated for transaction txn%03d-%s", 
                m_txnMessageCounter, m_txnNumber, priorTransactionName
            ));
        }
        super.setTransactionName(transactionName);
        if(transactionName!=null) {
            ++m_txnNumber;
            m_txnMessageCounter = 0;
        } else if(m_txnMessageCounter==0) {
            // An empty file will already exist, so
            // back off the transaction number so that
            // the next transaction inherits it.
            --m_txnNumber;
        }
        // m_transactionLU.m_appender.start();
    }
}
