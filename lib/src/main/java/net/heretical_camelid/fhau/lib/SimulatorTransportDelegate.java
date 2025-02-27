package net.heretical_camelid.fhau.lib;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SimulatorTransportDelegate extends TransportDelegateBase {

    // The simulator transport is configured by giving it a list of
    // regular expression patterns to recognize commands, each
    // linked to 0 or more response strings to be sent when that
    // command is recognized.
    HashMap<Pattern,String[]> m_programmedResponses;

    // Both commands and responses can contain placeholders.
    final static public String PLACEHOLDER = ":%%";

    // In regex patterns to recognize commands, each placeholder
    // is replaced by a pattern which matches 1 or more colon-separated
    // hex bytes as a group.
    final static private String PATTERN_REPLACEMENT = ")((:[0-9a-f]{2})+)";

    // In responses, the placeholder is replaced by a value derived
    // from the values of placeholder groups seen in the command
    // and/or files looked up using those values.
    // The rules for this mapping will be provided by a message protocol
    // object.
    MessageProtocolBase m_messageProtocol;


    public SimulatorTransportDelegate() {
        m_programmedResponses = new HashMap<>();
        m_messageProtocol = null;
    }

    public void setMessageProtocol(MessageProtocolBase mp) {
        m_messageProtocol = mp;
    }

    public void addProgrammedResponse(String commandPatternRegex, String[] responses ) {
        if(commandPatternRegex.contains(PLACEHOLDER)) {
            commandPatternRegex = "(" + commandPatternRegex.replace(PLACEHOLDER, PATTERN_REPLACEMENT);
        }
        Pattern commandPattern = Pattern.compile("^" + commandPatternRegex);
        m_programmedResponses.put(commandPattern, responses);
    }

    public String[] processCommand(String commandHexString, DeviceDelegateBase deviceDelegate) {
        for(Pattern p: m_programmedResponses.keySet()) {
            Matcher m = p.matcher(commandHexString);
            if( m.find() != true ) {
                continue;
            }
            /*
            for(int i=0; i<m.groupCount(); ++i) {
                System.out.println(m.group(i));
            }
             */
            if(m.groupCount()==0) {
                return m_programmedResponses.get(p);
            } else if(m_messageProtocol != null) {
                return m_messageProtocol.composeResponses(m);
            } else {
                throw new UnsupportedOperationException(
                    "SimulatorTransportDelegate requires a message protocol object to  support patterns containing placeholders"
                );
            }
        }
        return null;
    }

    public static void main(String[] args) {
        // train the delegate to respond to a selection of messages
        SimulatorTransportDelegate std = new SimulatorTransportDelegate();
        std.setMessageProtocol(new STDTest_MessageProtocol());

        // Taken from captures of LT40S startup
        String lt40sCmd1 = "08:00:8a:07:04:08:00:10:00:00:00";
        String lt40sRsp1_1Expected = "08:02:8a:07:04:08:00:10:00:00:00";
        std.addProgrammedResponse(lt40sCmd1, new String[] {lt40sRsp1_1Expected});
        String lt40sCmd2 = "08:00:b2:06:02:08:01:00:10:00";
        String lt40sRsp2_1Expected = "08:02:ba:06:07:0a:05:31:2e:30:2e:37";
        std.addProgrammedResponse(lt40sCmd2, new String[] {lt40sRsp2_1Expected});

        // Test based on command pattern containing subgroups
        // Presently throws UnsupportedOperationException
        String lt40sCmd3Pattern = "08:00:ca:06:02:08:%%";
        String lt40sCmd3Value = "08:00:ca:06:02:08:01";
        String lt40sRsp3_1Expected = "08:02:fa:01:87:11:0a:82:11:01:10:01:01:49:64:22:3a:20:22";
        std.addProgrammedResponse(lt40sCmd3Pattern, new String[] {lt40sRsp3_1Expected});

        // Tests based on expected commands
        String[] lt40sRsp1Actual = std.processCommand(lt40sCmd1, null);
        assert lt40sRsp1Actual.length == 1;
        assert lt40sRsp1Actual[0].equals(lt40sRsp1_1Expected);

        String[] lt40sRsp2Actual = std.processCommand(lt40sCmd2, null);
        assert lt40sRsp2Actual.length == 1;
        assert lt40sRsp2Actual[0].equals(lt40sRsp2_1Expected);

        String[] lt40sRsp3Actual = std.processCommand(lt40sCmd3Value, null);
        assert lt40sRsp3Actual.length == 1;
        assert lt40sRsp3Actual[0].equals(lt40sRsp3_1Expected);

        // Test based on unexpected command
        String[] unexpectedActual = std.processCommand("01:02:03", null);
        assert unexpectedActual == null;


        // TODO: Write tests for lt40sCmd3 when regex pattern matching in place
        System.out.println("All tests passed");
    }
}

class STDTest_MessageProtocol extends MessageProtocolBase {

    @Override
    String[] generateStartupCommands() {
        return new String[0];
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
        switch(m.group(1)) {
            case "08:00:ca:06:02:08":
                assert m.groupCount() == 3;
                return new String[] {
                    "08:02:fa:01:87:11:0a:82:11:%%:10:01:01:49:64:22:3a:20:22".replace(
                        SimulatorTransportDelegate.PLACEHOLDER, m.group(2)
                    )
                };
            default:
                return new String[] { };
        }
    }
}
