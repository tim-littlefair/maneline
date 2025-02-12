package net.heretical_camelid.fhau.lib;

import java.util.HashMap;
import java.util.regex.Pattern;

class SimulatorTransportDelegate extends TransportDelegateBase {
    HashMap<Pattern,String[]> m_programmedResponses;

    public SimulatorTransportDelegate() {
        m_programmedResponses = new HashMap<>();
    }

    public void addProgrammedResponse(Pattern commandPattern, String[] responses ) {
        m_programmedResponses.put(commandPattern, responses);
    }

    public String[] processCommand(String commandHexString, DeviceDelegateBase deviceDelegate) {
        return new String[] { "XXXX" };
    }

    public static void main(String[] args) {
        System.out.println("TODO: tests for SimulatorTransportDelegate");
    }
}
