.. code:: json

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
