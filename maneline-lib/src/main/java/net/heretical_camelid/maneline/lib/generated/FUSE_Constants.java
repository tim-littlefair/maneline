package net.heretical_camelid.maneline.lib.generated;

import java.util.Map;
import java.util.TreeMap;

import net.heretical_camelid.maneline.lib.utilities.Pair;

public class FUSE_Constants {

    public static Map<Integer, Pair<String,String>> _MODULE_NAMES_AND_TYPES = new TreeMap<>();
    static private void registerModule(int moduleId, String moduleName, String moduleType) {
        _MODULE_NAMES_AND_TYPES.put(
            moduleId,
            new Pair<String,String>(moduleType, moduleName)
        );
    }

    static {

        /* registerModule generated entries begin */
        registerModule(109,"Metal2000", "A");
        registerModule(114, "Super-Sonic", "A");
        registerModule(43, "Tape", "D");
        registerModule(58, "Large Hall", "R");
        /* registerModule generated entries end */

        /*
        7: ('S', 'Compressor'),
        11: ('R', '65FenderSpring'),
        18: ('M', 'Sine Chorus'),
        19: ('M', 'Tri Chorus'),
        21: ('D', 'Ducking'),
        22: ('D', 'Mono'),
        24: ('M', 'Sine Flanger'),
        25: ('M', 'Tri Flanger'),
        26: ('S', 'Fuzz'),
        31: ('M', 'Pitch Shift'),
        33: ('R', '63FenderSpring'),
        34: ('M', 'Ring Mod'),
        36: ('R', 'Small Hall'),
        38: ('R', 'Small Room'),
        41: ('M', 'Step Filter'),
        42: ('D', 'Stereo Tape'),
        43: ('D', 'Tape'),
        45: ('M', 'Vibratone'),
        58: ('R', 'Large Hall'),
        59: ('R', 'Large Room'),
        60: ('S', 'Overdrive'),
        64: ('M', 'VintageTrem'),
        65: ('M', 'SineTrem'),
        67: ('D', 'Echo Filter'),
        68: ('D', 'Multitap'),
        69: ('D', 'Ping Pong'),
        70: ('D', 'Reverse'),
        72: ('D', 'StEchoFilt'),
        73: ('S', 'Wah'),
        74: ('S', 'Touch Wah'),
        75: ('R', 'Large Plate'),
        76: ('R', 'Ambient'),
        77: ('R', 'Arena'),
        78: ('R', 'Small Plate'),
        79: ('M', 'Phaser'),
        83: ('A', "'65 Deluxe"),
        93: ('A', 'American90s'),
        94: ('A', "Brit '80s"),
        97: ('A', "Brit '60s"),
        100: ('A', "'59 Bassman"),
        103: ('A', "'57 Deluxe"),
        106: ('A', 'Princeton'),
        109: ('A', 'Metal 2000'),
        114: ('A', 'Super-Sonic'),
        117: ('A', "'65 Twin"),
        121: ('A', "Brit '70s"),
        124: ('A', "'57 Champ"),
        136: ('S', 'Simple Comp'),
        186: ('S', 'Greenbox'),
        241: ('A', 'Studio Pre'),
        244: ('M', 'Wah'),
        245: ('M', 'Touch Wah'),
        246: ('A', "'57 Twin"),
        249: ('A', "'60s Thrift"),
        252: ('A', 'Brit Colour'),
        255: ('A', 'Brit Watts'),
        259: ('S', 'Ranger'),
        271: ('S', 'Big Fuzz'),
        272: ('S', 'Orangebox'),
        273: ('S', 'Blackbox'),
        4127: ('M', 'Diatonic Pitch'),
         */
    }

}
