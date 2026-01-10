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
        registerModule(7, "Compressor", "S");
        registerModule(11, "65FenderSpring", "R");
        registerModule(18, "Sine Chorus", "M");
        registerModule(19, "Tri Chorus", "M");
        registerModule(21, "Ducking", "D");
        registerModule(22, "Mono", "D");
        registerModule(24, "Sine Flanger", "M");
        registerModule(25, "Tri Flanger", "M");
        registerModule(26, "Fuzz", "S");
        registerModule(31, "Pitch Shift", "M");
        registerModule(33, "63FenderSpring", "R");
        registerModule(34, "Ring Mod", "M");
        registerModule(36, "Small Hall", "R");
        registerModule(38, "Small Room", "R");
        registerModule(41, "Step Filter", "M");
        registerModule(42, "Stereo Tape", "D");
        registerModule(43, "Tape", "D");
        registerModule(45, "Vibratone", "M");
        registerModule(58, "Large Hall", "R");
        registerModule(59, "Large Room", "R");
        registerModule(60, "Overdrive", "S");
        registerModule(64, "VintageTrem", "M");
        registerModule(65, "SineTrem", "M");
        registerModule(67, "Echo Filter", "D");
        registerModule(68, "Multitap", "D");
        registerModule(69, "Ping Pong", "D");
        registerModule(70, "Reverse", "D");
        registerModule(72, "StEchoFilt", "D");
        registerModule(73, "Wah", "S");
        registerModule(74, "Touch Wah", "S");
        registerModule(75, "Large Plate", "R");
        registerModule(76, "Ambient", "R");
        registerModule(77, "Arena", "R");
        registerModule(78, "Small Plate", "R");
        registerModule(79, "Phaser", "M");
        registerModule(83, "'65 Deluxe", "A");
        registerModule(93, "American90s", "A");
        registerModule(94, "Brit '80s", "A");
        registerModule(97, "Brit '60s", "A");
        registerModule(100, "'59 Bassman", "A");
        registerModule(103, "'57 Deluxe", "A");
        registerModule(106, "Princeton", "A");
        registerModule(109, "Metal 2000", "A");
        registerModule(114, "Super-Sonic", "A");
        registerModule(117, "'65 Twin", "A");
        registerModule(121, "Brit '70s", "A");
        registerModule(124, "'57 Champ", "A");
        registerModule(136, "Simple Comp", "S");
        registerModule(186, "Greenbox", "S");
        registerModule(241, "Studio Pre", "A");
        registerModule(244, "Wah", "M");
        registerModule(245, "Touch Wah", "M");
        registerModule(246, "'57 Twin", "A");
        registerModule(249, "'60s Thrift", "A");
        registerModule(252, "Brit Colour", "A");
        registerModule(255, "Brit Watts", "A");
        registerModule(259, "Ranger", "S");
        registerModule(271, "Big Fuzz", "S");
        registerModule(272, "Orangebox", "S");
        registerModule(273, "Blackbox", "S");
        registerModule(4127, "Diatonic Pitch", "M");
        /* registerModule generated entries end */

    }

}
