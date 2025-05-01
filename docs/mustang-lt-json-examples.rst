Example JSON Preset definitions
===============================

When the FHAU desktop app retrieves preset definitions from the 
LT-40S amplifier, the JSON payload received looks like this:

.. include:: ../assets/FHAU____ONE_____-c9-eb4d.raw_preset.json.rst

When FHAU output is enabled and a payload like this is read,
it makes a verbatim copy in the output directory or zip file
with a filename ending .raw_preset.json, and also parses and 
reserializes the JSON data out with a filename ending .pretty_preset.json
in a format which is intended to be easier to read:

.. include:: ../assets/FHAU____ONE_____-c9-eb4d.pretty_preset.json.rst

As well as being more readable in terms of line length and whitespace 
spacing, the .pretty_preset.json is intended to be consistent 
in the ordering of JSON elements, so that similar presets can 
be compared, and so that presets which are actually identical in 
content but are serialized with different order of JSON elements
are able to be identified as being identical.

See the source file 
[PresetCanonicalSerializer.java](../lib/src/java/net/heretical_camelid/fhau/lib/Registries/PresetCanonicalSerializer.java)
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
