package net.heretical_camelid.fhau.lib;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract public class MessageProtocolBase {
    abstract String[] generateStartupCommands();
    abstract void parseReport(String report, DeviceDelegateBase deviceDelegate);
    Matcher prefixMatchTest(Pattern pattern, String reportHexString) {
        return pattern.matcher(reportHexString);
    }
    abstract public String[] composeResponses(Matcher m);

    public static void main(String[] args) {
        System.out.println("TODO: tests");
    }
}
