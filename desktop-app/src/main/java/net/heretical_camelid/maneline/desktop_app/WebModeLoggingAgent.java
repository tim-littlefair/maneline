package net.heretical_camelid.maneline.desktop_app;

/*
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.simple.SimpleLogger;
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.logstash.logback.argument.StructuredArguments.kv;

import net.heretical_camelid.maneline.lib.interfaces.LoggingAgentBase;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class WebModeLoggingAgent extends LoggingAgentBase {
    private static final Logger m_logger = LoggerFactory.getLogger("FILE");

    private static PrintStream m_sessionLog;

    static WebModeLoggingAgent s_instance = null;

    static void setSessionNameStatic(String sessionName) {
        assert s_instance != null;
        s_instance.setSessionName(sessionName);
    }

    public WebModeLoggingAgent() {
        super();

        // Hopefully this is only instantiated once
        assert s_instance == null;
        s_instance = this;
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
        } else if(getTransactionName() != null) {
            m_logger.info(messageToAppend, o);
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

    @Override
    public void setTransactionName(String transactionName) {
        assert getSessionName() != null:
            "Session name must be set before setting transaction name"
        ;
        if(transactionName!=null) {
            super.setTransactionName(transactionName);
            LogbackRollingPolicy_SingleMessagePerFile.setFilenamePattern(
                String.format("%s/%s-%%03d.%s",getSessionName(), transactionName,"json")
            );
        } else {
            super.setTransactionName(null);
        }
    }
}
