package net.heretical_camelid.fhau.lib;

abstract class TransportDelegateBase {
    abstract public String[] processCommand(String commandHexString, DeviceDelegateBase deviceDelegate);

    public static void main(String[] args) {
        System.out.println("TODO: tests for TransportDelegateBase");
    }
}
