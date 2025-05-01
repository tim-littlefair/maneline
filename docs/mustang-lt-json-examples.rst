Example JSON Preset definitions
===============================

When the FHAU desktop app retrieves preset definitions from the 
LT-40S amplifier, the JSON payload received looks like this:

.. code:: JSON

  {"nodeType":"preset","nodeId":"preset","version":"1.1","numInputs":2,"numOutputs":2,"info":{"displayName":"FHAU    ONE     ","preset_id":"82701e3e-caf7-11e7-b721-171e6c7d3090","author":"","source_id":"","timestamp":1510855005,"created_at":0,"product_id":"mustang-lt","is_factory_default":true,"bpm":0},"audioGraph":{"nodes":[{"nodeId":"stomp","nodeType":"dspUnit","FenderId":"DUBS_Overdrive","dspUnitParameters":{"bypass":false,"bypassType":"Post","level":0.500000,"gain":0.500000,"low":0.500000,"mid":0.500000,"high":0.500000}},{"nodeId":"mod","nodeType":"dspUnit","FenderId":"DUBS_Vibratone","dspUnitParameters":{"bypass":false,"bypassType":"Post","level":0.960000,"rotor":5.670000,"tapTimeBPM":340.200012,"noteDivision":"off","depth":0.180000,"feedback":0.680000,"phase":0.520000}},{"nodeId":"amp","nodeType":"dspUnit","FenderId":"DUBS_Deluxe57","dspUnitParameters":{"volume":-6.413170,"gatePreset":"off","gateDetectorPosition":"jack","cabsimType":"57dlx","gain":0.600000,"treb":0.720000,"mid":0.500000,"bass":0.500000,"sag":"match","bias":0.500000}},{"nodeId":"delay","nodeType":"dspUnit","FenderId":"DUBS_ReverseDelay","dspUnitParameters":{"bypass":false,"bypassType":"Pre","level":0.750000,"time":0.400000,"tapTimeBPM":150,"noteDivision":"off","feedback":0.300000,"attenuate":1,"chase":0.650000}},{"nodeId":"reverb","nodeType":"dspUnit","FenderId":"DUBS_SmallRoomReverb","dspUnitParameters":{"bypass":false,"bypassType":"Pre","level":0.500000,"decay":0.500000,"dwell":0.500000,"diffuse":0.500000,"tone":0.500000}}],"connections":[{"input":{"nodeId":"preset","index":0},"output":{"nodeId":"stomp","index":0}},{"input":{"nodeId":"preset","index":1},"output":{"nodeId":"stomp","index":1}},{"input":{"nodeId":"stomp","index":0},"output":{"nodeId":"mod","index":0}},{"input":{"nodeId":"stomp","index":1},"output":{"nodeId":"mod","index":1}},{"input":{"nodeId":"mod","index":0},"output":{"nodeId":"amp","index":0}},{"input":{"nodeId":"mod","index":1},"output":{"nodeId":"amp","index":1}},{"input":{"nodeId":"amp","index":0},"output":{"nodeId":"delay","index":0}},{"input":{"nodeId":"amp","index":1},"output":{"nodeId":"delay","index":1}},{"input":{"nodeId":"delay","index":0},"output":{"nodeId":"reverb","index":0}},{"input":{"nodeId":"delay","index":1},"output":{"nodeId":"reverb","index":1}},{"input":{"nodeId":"reverb","index":0},"output":{"nodeId":"preset","index":0}},{"input":{"nodeId":"reverb","index":1},"output":{"nodeId":"preset","index":1}}]}}


When FHAU output is enabled and a payload like this is read,
it makes a verbatim copy in the output directory or zip file
with a filename ending .raw_preset.json, and also parses and 
reserializes the JSON data out with a filename ending .pretty_preset.json
in a format which is intended to be easier to read:

.. code:: JSON

  {
    "nodeType": "preset",
    "version": "1.1",
    "numInputs": 2,
    "numOutputs": 2,
    "info": {
      "displayName": "FHAU    ONE     ",
      "preset_id": "82701e3e-caf7-11e7-b721-171e6c7d3090",
      "author": "",
      "source_id": "",
      "timestamp": 1510855005,
      "created_at": 0,
      "is_factory_default": true,
      "bpm": 0
    },
    "audioGraph": {
      "nodes": [
        {
          "nodeId": "stomp",
          "FenderId": "DUBS_Overdrive",
          "dspUnitParameters": {
            "bypass": false,
            "bypassType": "Post",
            "level": 0.5,
            "gain": 0.5
          }
        },
        {
          "nodeId": "mod",
          "FenderId": "DUBS_Vibratone",
          "dspUnitParameters": {
            "bypass": false,
            "bypassType": "Post",
            "level": 0.96,
            "gain": 0.0
          }
        },
        {
          "nodeId": "amp",
          "FenderId": "DUBS_Deluxe57",
          "dspUnitParameters": {
            "bypass": false,
            "level": 0.0,
            "gain": 0.6
          }
        },
        {
          "nodeId": "delay",
          "FenderId": "DUBS_ReverseDelay",
          "dspUnitParameters": {
            "bypass": false,
            "bypassType": "Pre",
            "level": 0.75,
            "gain": 0.0
          }
        },
        {
          "nodeId": "reverb",
          "FenderId": "DUBS_SmallRoomReverb",
          "dspUnitParameters": {
            "bypass": false,
            "bypassType": "Pre",
            "level": 0.5,
            "gain": 0.0,
            "tone": "0.500000"
          }
        }
      ],
      "connections": [
        {
          "input": {
            "index": 0,
            "nodeId": "preset"
          },
          "output": {
            "index": 0,
            "nodeId": "stomp"
          }
        },
        {
          "input": {
            "index": 1,
            "nodeId": "preset"
          },
          "output": {
            "index": 1,
            "nodeId": "stomp"
          }
        },
        {
          "input": {
            "index": 0,
            "nodeId": "stomp"
          },
          "output": {
            "index": 0,
            "nodeId": "mod"
          }
        },
        {
          "input": {
            "index": 1,
            "nodeId": "stomp"
          },
          "output": {
            "index": 1,
            "nodeId": "mod"
          }
        },
        {
          "input": {
            "index": 0,
            "nodeId": "mod"
          },
          "output": {
            "index": 0,
            "nodeId": "amp"
          }
        },
        {
          "input": {
            "index": 1,
            "nodeId": "mod"
          },
          "output": {
            "index": 1,
            "nodeId": "amp"
          }
        },
        {
          "input": {
            "index": 0,
            "nodeId": "amp"
          },
          "output": {
            "index": 0,
            "nodeId": "delay"
          }
        },
        {
          "input": {
            "index": 1,
            "nodeId": "amp"
          },
          "output": {
            "index": 1,
            "nodeId": "delay"
          }
        },
        {
          "input": {
            "index": 0,
            "nodeId": "delay"
          },
          "output": {
            "index": 0,
            "nodeId": "reverb"
          }
        },
        {
          "input": {
            "index": 1,
            "nodeId": "delay"
          },
          "output": {
            "index": 1,
            "nodeId": "reverb"
          }
        },
        {
          "input": {
            "index": 0,
            "nodeId": "reverb"
          },
          "output": {
            "index": 0,
            "nodeId": "preset"
          }
        },
        {
          "input": {
            "index": 1,
            "nodeId": "reverb"
          },
          "output": {
            "index": 1,
            "nodeId": "preset"
          }
        }
      ]
    }
  }

As well as being more readable in terms of line length and whitespace 
spacing, the .pretty_preset.json is intended to be consistent 
in the ordering of JSON elements, so that similar presets can 
be compared, and so that presets which are actually identical in 
content but are serialized with different order of JSON elements
are able to be identified as being identical.

See the source file 
(PresetCanonicalSerializer.java|https://github.com/tim-littlefair/feral-horse-amp-utils/blob/main/lib/src/main/java/net/heretical_camelid/fhau/lib/registries/PresetCanonicalSerializer.java)
for details of how FHAU reads and writes the JSON format.

Some initial notes on this format:

- At the top level of the JSON tree, there are 4 attributes (nodeType,
  version, numInputs and numOutputs) and three subtrees (info, audioGraph
  and connections).
- The sound quality of the preset is almost primarily determined by the 
  content of the audioGraph subtree.
- The connections subtree defines the sequence of processors the audio
  will pass through.  Typically this will reflect 
  [this illustration from one of the FMIC documents about the LT40S](../assets/LT40S-signal-path.png),
  but it is possible (but probably a very bad idea) to configure presets in which 
  the effects are not in this canonical order.

*NB In the interests of respecting any copyright FMIC may hold over the
factory presets stored in the amplifier on purchase or after a factory 
reset, the example preset rendered in the two files above was manually 
created by myself starting from the empty preset state and adding random 
effects.*

*It sounds awful, I don't recommend anyone listening to it.*
