package net.heretical_camelid.maneline.lib.generated;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.heretical_camelid.maneline.lib.utilities.Pair;

public class FUSE_Constants {

    public static Map<Integer, Pair<String,String>> _MODULE_NAMES_AND_TYPES = new TreeMap<>();
    public static Map<Pair<Integer,Integer>, Pair<String,Integer>> _MODULE_PARAMS = new TreeMap<>();
    static private void registerModule(int moduleId, String moduleName, String moduleType) {
        _MODULE_NAMES_AND_TYPES.put(
            moduleId,
            new Pair<String,String>(moduleType, moduleName)
        );
    }

    static private void registerModuleParam(
        int paramId, String paramName, int paramType, List<Integer> paramModuleIds
    ) {
        for(int moduleId: paramModuleIds) {
            _MODULE_PARAMS.put(
                new Pair(moduleId, paramId),
                new Pair(paramName, paramType)
            );
        }
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

        /* registerModuleParam generated entries begin */
        registerModuleParam(
            0, "Level", 1, 
            Arrays.asList(7, 11, 18, 19, 21, 22, 24, 25, 26, 33, 34, 36, 38, 41, 42, 43, 45, 58, 59, 60, 64, 65, 67, 68, 69, 70, 72, 75, 76, 77, 78, 79, 186, 259, 271, 272, 273)
        );
        registerModuleParam(
            0, "Mix", 1, 
            Arrays.asList(31, 73, 74, 244, 245, 4127)
        );
        registerModuleParam(
            0, "Type", 146, 
            Arrays.asList(136)
        );
        registerModuleParam(
            0, "Volume", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            1, "Decay", 1, 
            Arrays.asList(11, 33, 36, 38, 58, 59, 75, 76, 77, 78)
        );
        registerModuleParam(
            1, "Delay Time", 6, 
            Arrays.asList(21, 22, 42, 43, 67, 70)
        );
        registerModuleParam(
            1, "Delay Time", 7, 
            Arrays.asList(69, 72)
        );
        registerModuleParam(
            1, "Delay Time", 8, 
            Arrays.asList(68)
        );
        registerModuleParam(
            1, "Dist", 1, 
            Arrays.asList(272)
        );
        registerModuleParam(
            1, "Distortion", 1, 
            Arrays.asList(273)
        );
        registerModuleParam(
            1, "Frequency", 1, 
            Arrays.asList(34, 73, 244)
        );
        registerModuleParam(
            1, "Gain", 1, 
            Arrays.asList(26, 60, 186, 259)
        );
        registerModuleParam(
            1, "Gain", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            1, "Pitch", 19, 
            Arrays.asList(31)
        );
        registerModuleParam(
            1, "Pitch", 152, 
            Arrays.asList(4127)
        );
        registerModuleParam(
            1, "Rate", 14, 
            Arrays.asList(64, 65)
        );
        registerModuleParam(
            1, "Rate", 16, 
            Arrays.asList(18, 19, 24, 25, 41, 79)
        );
        registerModuleParam(
            1, "Rotor Speed", 15, 
            Arrays.asList(45)
        );
        registerModuleParam(
            1, "Sensitivity", 1, 
            Arrays.asList(74, 245)
        );
        registerModuleParam(
            1, "Threshold", 1, 
            Arrays.asList(7)
        );
        registerModuleParam(
            1, "Tone", 1, 
            Arrays.asList(271)
        );
        registerModuleParam(
            2, "Depth", 1, 
            Arrays.asList(18, 19, 24, 25, 34, 45, 79)
        );
        registerModuleParam(
            2, "Duty Cycle", 1, 
            Arrays.asList(64, 65)
        );
        registerModuleParam(
            2, "Dwell", 1, 
            Arrays.asList(11, 33, 36, 38, 58, 59, 75, 76, 77, 78)
        );
        registerModuleParam(
            2, "FFdbk", 1, 
            Arrays.asList(70)
        );
        registerModuleParam(
            2, "Feedback", 1, 
            Arrays.asList(21, 22, 42, 43, 67, 68, 69, 72)
        );
        registerModuleParam(
            2, "Filter", 1, 
            Arrays.asList(273)
        );
        registerModuleParam(
            2, "Gain2", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            2, "Heel Freq", 1, 
            Arrays.asList(73, 244)
        );
        registerModuleParam(
            2, "Key", 153, 
            Arrays.asList(4127)
        );
        registerModuleParam(
            2, "LoCut", 1, 
            Arrays.asList(259)
        );
        registerModuleParam(
            2, "Low", 1, 
            Arrays.asList(60)
        );
        registerModuleParam(
            2, "Min Freq", 1, 
            Arrays.asList(74, 245)
        );
        registerModuleParam(
            2, "Octave", 1, 
            Arrays.asList(26)
        );
        registerModuleParam(
            2, "Pre Delay", 1, 
            Arrays.asList(31)
        );
        registerModuleParam(
            2, "Ratio", 4, 
            Arrays.asList(7)
        );
        registerModuleParam(
            2, "Resonance", 1, 
            Arrays.asList(41)
        );
        registerModuleParam(
            2, "Sustain", 1, 
            Arrays.asList(271)
        );
        registerModuleParam(
            2, "Tone", 1, 
            Arrays.asList(186, 272)
        );
        registerModuleParam(
            3, "Attack Time", 1, 
            Arrays.asList(7, 64)
        );
        registerModuleParam(
            3, "Average Delay", 1, 
            Arrays.asList(18, 19)
        );
        registerModuleParam(
            3, "Blend", 18, 
            Arrays.asList(186)
        );
        registerModuleParam(
            3, "Bright", 1, 
            Arrays.asList(259)
        );
        registerModuleParam(
            3, "Brightness", 1, 
            Arrays.asList(22, 68, 69)
        );
        registerModuleParam(
            3, "Diffusion", 1, 
            Arrays.asList(11, 33, 36, 38, 58, 59, 75, 76, 77, 78)
        );
        registerModuleParam(
            3, "Feedback", 1, 
            Arrays.asList(24, 25, 31, 45, 79)
        );
        registerModuleParam(
            3, "Flutter", 1, 
            Arrays.asList(42, 43)
        );
        registerModuleParam(
            3, "Frequency", 1, 
            Arrays.asList(67, 72)
        );
        registerModuleParam(
            3, "LFO Clipping", 1, 
            Arrays.asList(65)
        );
        registerModuleParam(
            3, "LFO Shape", 140, 
            Arrays.asList(34)
        );
        registerModuleParam(
            3, "Low", 1, 
            Arrays.asList(26)
        );
        registerModuleParam(
            3, "Master Volume", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            3, "Max Freq", 1, 
            Arrays.asList(74, 245)
        );
        registerModuleParam(
            3, "Mid", 1, 
            Arrays.asList(60)
        );
        registerModuleParam(
            3, "Min Freq", 1, 
            Arrays.asList(41)
        );
        registerModuleParam(
            3, "RFdbk", 1, 
            Arrays.asList(70)
        );
        registerModuleParam(
            3, "Release", 1, 
            Arrays.asList(21)
        );
        registerModuleParam(
            3, "Scale", 154, 
            Arrays.asList(4127)
        );
        registerModuleParam(
            3, "Toe Freq", 1, 
            Arrays.asList(73, 244)
        );
        registerModuleParam(
            4, "Attenuation", 1, 
            Arrays.asList(22)
        );
        registerModuleParam(
            4, "Brightness", 1, 
            Arrays.asList(43)
        );
        registerModuleParam(
            4, "High", 1, 
            Arrays.asList(26, 60)
        );
        registerModuleParam(
            4, "High Q", 129, 
            Arrays.asList(73, 74, 244, 245)
        );
        registerModuleParam(
            4, "LFO Phase", 1, 
            Arrays.asList(34)
        );
        registerModuleParam(
            4, "LFO Shape", 140, 
            Arrays.asList(79)
        );
        registerModuleParam(
            4, "LR Phase", 1, 
            Arrays.asList(18, 19, 24, 25, 45)
        );
        registerModuleParam(
            4, "Max Freq", 1, 
            Arrays.asList(41)
        );
        registerModuleParam(
            4, "Mode", 139, 
            Arrays.asList(68)
        );
        registerModuleParam(
            4, "Release Time", 1, 
            Arrays.asList(7, 64)
        );
        registerModuleParam(
            4, "Resonance", 1, 
            Arrays.asList(67, 72)
        );
        registerModuleParam(
            4, "Separation", 1, 
            Arrays.asList(42)
        );
        registerModuleParam(
            4, "Stereo", 1, 
            Arrays.asList(69)
        );
        registerModuleParam(
            4, "Threshold", 1, 
            Arrays.asList(21)
        );
        registerModuleParam(
            4, "Tone", 1, 
            Arrays.asList(11, 31, 33, 36, 38, 58, 59, 70, 75, 76, 77, 78, 4127)
        );
        registerModuleParam(
            4, "Treble", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            4, "Tri Shaping", 1, 
            Arrays.asList(65)
        );
        registerModuleParam(
            5, "Brightness", 1, 
            Arrays.asList(42)
        );
        registerModuleParam(
            5, "Input Level", 1, 
            Arrays.asList(67, 72)
        );
        registerModuleParam(
            5, "Middle", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            5, "Stereo", 1, 
            Arrays.asList(43)
        );
        registerModuleParam(
            6, "Bass", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            7, "Presence", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            8, "Resonance", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            9, "Noise Gate Depth", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            10, "Bias", 13, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            11, "Other_11", 12, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            12, "Other_12", 141, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            13, "Other_13", 141, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            14, "Other_14", 141, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            15, "Noise Gate", 144, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            16, "Noise Gate Thresh", 134, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            17, "Cab", 142, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            18, "Power Supply", 141, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            19, "Sag", 143, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            20, "Other_20", 141, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            21, "Other_21", 141, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        registerModuleParam(
            22, "Other_22", 0, 
            Arrays.asList(83, 93, 94, 97, 100, 103, 106, 109, 114, 117, 121, 124, 241, 246, 249, 252, 255)
        );
        /* registerModuleParam generated entries end */
    }

}
