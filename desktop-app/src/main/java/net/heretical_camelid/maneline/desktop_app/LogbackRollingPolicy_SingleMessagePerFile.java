// package net.heretical_camelid.fhau.desktop_app;
package net.heretical_camelid.maneline.desktop_app;

import java.io.File;

import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingPolicyBase;
import ch.qos.logback.core.rolling.RolloverFailure;
import ch.qos.logback.core.rolling.TriggeringPolicy;

/**
 * For the FHAU web capability, we are using the SL4J
 * framework to capture a single JSON file per log
 * message.
 * The setFilenamePattern static method on this class
 * allows us to group messages into directories by
 * session, and within sessions into transactions by
 * filename prefix.
 */
public class LogbackRollingPolicy_SingleMessagePerFile<E>
    extends RollingPolicyBase
    implements TriggeringPolicy<E> {


    static String fileNamePatternStr;
    static private int rollingNumber = 0;

    static private LogbackRollingPolicy_SingleMessagePerFile s_instance = null;

    static public void setFilenamePattern(String fnpStr) {
        fileNamePatternStr = fnpStr;
        rollingNumber = 0;
        if(s_instance!=null) {
            s_instance.rollover();
        }
    }

    FileAppender<?> parentAppender;

    public LogbackRollingPolicy_SingleMessagePerFile() {
        super();
        setFilenamePattern("log-%03d.txt");
        assert s_instance == null;
        s_instance = this;
    }

    /**
     * This class requires access to the appender object
     * so that it can set the filename
     * @param appender
     */
    @Override
    public void setParent(FileAppender<?> appender) {
        super.setParent(appender);
        this.parentAppender = appender;
    }

    @Override
    public void rollover() throws RolloverFailure {
        String nextLogPath = String.format(fileNamePatternStr,rollingNumber);
        parentAppender.setFile(nextLogPath);
        rollingNumber++;
    }

    @Override
    public String getActiveFileName() {
        return getParentsRawFileProperty();
    }

    @Override
    public boolean isTriggeringEvent(File activeFile, final E event) {
        return true;
    }
}
