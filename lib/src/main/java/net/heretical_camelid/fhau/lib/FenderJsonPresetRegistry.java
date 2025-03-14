package net.heretical_camelid.fhau.lib;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;


/**
 * PresetRegistryBase is a minimal registry of presets which maintains
 * a collection of simple preset objects which consist of a slot number
 * and name only.
 * Both the registry class and the simple preset objects can be extended
 * to support more complex behaviour.
 * Note that the base registry class is responsible for creating the
 * record objects it stores.  Classes which extend the base registry
 * class to are expected to create record objects which contain additional
 * data beyond the slot index and name, so the name, of a more Extended implementations of the registry are expected
 *
 */
public class FenderJsonPresetRegistry extends PresetRegistryBase {
    public FenderJsonPresetRegistry() {  }

    @Override
    public void register(int slotIndex, String name, byte[] definition) {
        // Slots are numbered from 1
        assert slotIndex > 0;
        // This registry requires a definition
        assert definition != null;

        m_records.put(slotIndex, new FenderJsonPresetRecord(name, definition));
    }

    public void generatePresetDetails(PrintStream printStream) {
        acceptVisitor(new PresetDetailsTableGenerator(printStream));
    }

    @Override
    public void dump(String outputPathPrefix) {
        generatePresetDetails(System.out);
    }
}

class FenderJsonPresetRecord extends PresetRecordBase {
    final String m_definitionRawJson;
    final JsonObject m_definitionJsonObject;

    public FenderJsonPresetRecord(String name, byte[] definitionBytes) {
        super(name);
        m_definitionRawJson = new String(definitionBytes, StandardCharsets.UTF_8);
        m_definitionJsonObject = JsonParser.parseString(
            m_definitionRawJson
        ).getAsJsonObject();
    }

    public String getValue(String itemJsonPath) {
        JsonElement je = m_definitionJsonObject;
        String[] pathElements = itemJsonPath.split("/");
        for(String pe: pathElements) {
            if("01234".contains(pe)) {
                JsonArray joAsArray = je.getAsJsonArray();
                assert joAsArray != null;
                je = joAsArray.get(Integer.parseInt(pe));
            } else {
                je = je.getAsJsonObject().get(pe);
            }
            if(je==null) {
                return null;
            }
        }
        return je.getAsString();
    }

    public String displayName() {
        return getValue("info/displayName");
    }

    public String ampName() {
        return getValue("audioGraph/nodes/2/FenderId").replace("DUBS_","");
    }

    public String effects() {
        return String.format("%s %s %s %s",
            dspUnitDesc(0), dspUnitDesc(1),
            // the amp is at nodeIndex=2
            dspUnitDesc(3), dspUnitDesc(4)
        );
    }

    public String dspUnitDesc(int nodeIndex) {
        String nodePrefix = String.format("audioGraph/nodes/%d/",nodeIndex);
        String nodeType = getValue(nodePrefix+"nodeId");
        String nodeName = getValue(nodePrefix+"FenderId").replace("DUBS_","");
        return nodeType + ":" + nodeName;
    }
}

class PresetDetailsTableGenerator implements PresetRegistryVisitor {
    private final static String _LINE_FORMAT = "%3d %-16s %-20s %-60s";
    PrintStream m_printStream;
    PresetDetailsTableGenerator(PrintStream printStream) {
        m_printStream = printStream;
    }
    @Override
    public void visit(PresetRegistryBase registry) {
        m_printStream.println("Presets");
        m_printStream.println(String.format(
            _LINE_FORMAT.replace("%3d", "%3s")
            "#", "Name", "Amplifier","Effects"
        ));
    }

    @Override
    public void visit(int slotIndex, Object record) {
        FenderJsonPresetRecord fjpr = (FenderJsonPresetRecord) record;
        assert fjpr != null;
        m_printStream.println(String.format(
            _LINE_FORMAT,
            slotIndex, fjpr.displayName(), fjpr.ampName(), fjpr.effects()
        ));
    }
}