package net.heretical_camelid.fhau.lib.delegates;

import java.util.regex.Matcher;

public class MessageProtocol_LT40SOld extends MessageProtocolBase_Old
{
    public static final String CMD1_OPEN = "08:00:8a:07:04:08:00:10:00:00:00";
    public static final String RSP1_1 = "08:02:8a:07:04:08:00:10:00:00:00";
    public static final String CMD2_GET_FW_VER = "08:00:b2:06:02:08:01:00:10:00";
    public static final String RSP2_1_V1_0_7 = "08:02:ba:06:07:0a:05:31:2e:30:2e:37";
    public static final String CMDX_GET_PRESET_JSON = "08:00:ca:06:02:08:%%";
    public static final String RSPX_PRESET_JSON = "08:02:fa:01:%%:0a:%%:7b:%%:10";

    public MessageProtocol_LT40SOld() {
        addProgrammedResponse(CMD1_OPEN, new String[] {RSP1_1});
        addProgrammedResponse(CMD2_GET_FW_VER, new String[] {RSP2_1_V1_0_7});
        addProgrammedResponse(CMDX_GET_PRESET_JSON,new String[] {RSPX_PRESET_JSON});
    }
    public String[] generateStartupCommands() {
        return new String[] { CMD1_OPEN, CMD2_GET_FW_VER };
    }

    @Override
    String[] generatePresetChangeCommands() {
        return new String[0];
    }

    @Override
    String[] generatePresetChangeCommands(int presetIndex) {
        String presetIndexHex = String.format("%02x",presetIndex);
        return new String[] {
            CMDX_GET_PRESET_JSON.replace(PLACEHOLDER,":"+presetIndexHex)
        };
    }

    @Override
    void parseReport(String report, DeviceDelegateBase deviceDelegate) {
    }

    @Override
    public String[] composeResponses(Matcher m) {
        if(m.group(1).equals(CMDX_GET_PRESET_JSON.replace(PLACEHOLDER,""))) {
            assert m.groupCount() == 3;
            String presetIndexHex = m.group(2);
            return new String[]{
                "08:02:fa:01:87:11:0a:82:11:%%:10:01:01:49:64:22:3a:20:22".replace(
                    PLACEHOLDER, presetIndexHex
                )
            };
        } else {
            return null;
        }
    }

    public void parseReport(String[] report, DeviceDelegateBase deviceDelegate) {

    }

    public static void main(String[] args) {
        System.out.println("HW");
    }
}
