package net.heretical_camelid.fhau.lib;

public class MessageProtocol_LT40S // implements IMessageProtocol
{
    public String[] generateStartupCommands() {
        return MessageConstants_LT40S.STARTUP_COMMANDS;
    }

    public void parseReport(String[] report, DeviceDelegateBase deviceDelegate) {

    }

    public static void main(String[] args) {
        System.out.println("HW");
    }
}
