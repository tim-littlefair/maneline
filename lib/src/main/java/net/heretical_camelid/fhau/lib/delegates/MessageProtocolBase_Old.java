package net.heretical_camelid.fhau.lib.delegates;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This class has two separate but related functions:
 * <ul>
 *     <li>
 *         the primary function is to generate the commands
 *         to be sent to a real or simulated device;
 *     </li>
 *     <li>
 *         the secondary function is, for a simulated device,
 *         to generate responses which reflect the behaviour
 *         of a physical device.
 *     </li>
 * </ul>
 */
abstract public class MessageProtocolBase_Old {
    // Both commands and responses can contain placeholders.
    final static public String PLACEHOLDER = ":%%";

    // In regex patterns to recognize commands, each placeholder
    // is replaced by a pattern which matches 1 or more colon-separated
    // hex bytes as a group.
    final static private String PATTERN_REPLACEMENT = ")((:[0-9a-f]{2})+)";

    // The secondary simulator behaviour of this class is configured by
    // giving it a list of regular expression patterns to recognize
    // commands, each linked to 0 or more response strings to be sent
    // when that command is recognized.
    private HashMap<Pattern,String[]> m_programmedResponses;

    protected MessageProtocolBase_Old() {
        m_programmedResponses = new HashMap<>();
    }

    // The primary protocol behaviour of this class provided by
    // implementation class definitions of the following three
    // functions.
    abstract String[] generateStartupCommands();
    abstract String[] generatePresetChangeCommands();

    abstract String[] generatePresetChangeCommands(int presetIndex);

    abstract void parseReport(String report, DeviceDelegateBase deviceDelegate);

    public void addProgrammedResponse(String commandPatternRegex, String[] responses ) {
        if(commandPatternRegex.contains(PLACEHOLDER)) {
            commandPatternRegex = "(" + commandPatternRegex.replace(PLACEHOLDER, PATTERN_REPLACEMENT);
        }
        Pattern commandPattern = Pattern.compile("^" + commandPatternRegex);
        m_programmedResponses.put(commandPattern, responses);
    }
    abstract public String[] composeResponses(Matcher m);

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
            } else  {
                return composeResponses(m);
            }
        }
        return null;
    }

    public static void main(String[] args) {
        System.out.println("TODO: tests");
    }
}
