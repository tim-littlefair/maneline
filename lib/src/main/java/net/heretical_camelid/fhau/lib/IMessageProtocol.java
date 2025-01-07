package net.heretical_camelid.fhau.lib;

public interface IMessageProtocol {
    byte[][] generateStartupCommands();
    void parseReport(byte[] report);
}
