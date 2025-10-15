#!/usr/bin/lua

-- sessions.lua
-- The purpose of this package is to contain all logic related to
-- supporting download of presets, logs etc from directories on
-- the Maneline server associated with the current session or any
-- of the recent retained sessions.

-- Part of the maneline project released under GPL 2.0
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/maneline/blob/main/LICENSE

local lfs = require('lfs')
local cjson = require('cjson.safe')

local SessionPresets = {}

function SessionPresets:get_session_presets(session_name)
    preset_rows = {}
    preset_row = {}
    preset_row.filenamePrefix="60S_____FUZZ____-0af8-3fbc"
    preset_row.slot="1"
    preset_row.displayName="60S     FUZZ    "
    preset_row.module1="VariFuzz"
    preset_row.module2="Passthru"
    preset_row.module3="Plexi87"
    preset_row.module4="TapeDelayLite"
    preset_row.module5="LargePlate"
    table.insert(preset_rows, preset_row)
    print(cjson.encode(preset_rows))
    return preset_rows
end

return SessionPresets

