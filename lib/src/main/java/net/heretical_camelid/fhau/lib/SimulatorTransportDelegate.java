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
        // train the delegate to respond to a selection of messages
        SimulatorTransportDelegate std = new SimulatorTransportDelegate();
        // Taken from captures of LT40S startup
        String lt40sCmd1 = "08:00:8a:07:04:08:00:10:00:00:00";
        String lt40sRsp1_1Expected = "08:02:8a:07:04:08:00:10:00:00:00";
        String lt40sCmd2 = "08:00:b2:06:02:08:01:00:10:00";
        String lt40sRsp2_1Expected = "08:02:ba:06:07:0a:05:31:2e:30:2e:37";
        String lt40sCmd3 = "08:00:ca:06:02:08:01:01:00:10";
        String lt40sRsp3_1Expected = "08:02:fa:01:87:11:0a:82:11:%PS_JSON%:10:01:01:49:64:22:3a:20:22";
        std.addProgrammedResponse(
            Pattern.compile(lt40sCmd1), new String[] { lt40sRsp1_1Expected }
        );
        std.addProgrammedResponse(
            Pattern.compile(lt40sCmd2), new String[] { lt40sRsp2_1Expected }
        );

        // Tests based on expected commands
        String[] lt40sRsp1Actual = std.processCommand(lt40sCmd1, null);
        assert lt40sRsp1Actual.length == 1;
        assert lt40sRsp1Actual[0].equals(lt40sRsp1_1Expected);

        String[] lt40sRsp2Actual = std.processCommand(lt40sCmd1, null);
        assert lt40sRsp2Actual.length == 1;
        assert lt40sRsp2Actual[0].equals(lt40sRsp1_1Expected);

        // Test based on unexpected command
        String[] unexpectedActual = std.processCommand("01:02:03", null);
        assert unexpectedActual == null;

        // TODO: Write tests for lt40sCmd3 when regex pattern matching in place
        System.out.println("All tests passed");
    }
}
