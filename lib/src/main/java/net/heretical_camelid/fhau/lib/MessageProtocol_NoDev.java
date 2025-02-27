package net.heretical_camelid.fhau.lib;

import java.util.regex.Matcher;

public class MessageProtocol_NoDev extends MessageProtocolBase
{
    public String[] generateStartupCommands() {
        return MessageConstants_LT40S.STARTUP_COMMANDS;
    }

    @Override
    String[] generatePresetChangeCommands() {
        return new String[0];
    }

    @Override
    String[] generatePresetChangeCommands(int presetIndex) {
        return new String[0];
    }

    @Override
    void parseReport(String report, DeviceDelegateBase deviceDelegate) {

    }

    @Override
    public String[] composeResponses(Matcher m) {
        // Presently based on LT40S
        assert m.groupCount()>2;
        return new String[0];
    }

    public void parseReport(String[] report, DeviceDelegateBase deviceDelegate) {

    }

    public static void main(String[] args) {
        System.out.println("HW");
    }
}
