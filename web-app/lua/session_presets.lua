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

function _csv_get_next_field(line,field_start_pos)
    local field_value, next_field_pos
    next_comma_pos=line:find(",",field_start_pos)
    if next_comma_pos
    then
        field_value = line:sub(field_start_pos,next_comma_pos-1)
        next_field_pos=next_comma_pos+1
    else
        field_value = line:sub(field_start_pos,#line)
        next_field_pos=nil
    end
    return field_value, next_field_pos
end

function SessionPresets:get_session_presets(session_name)
    local preset_rows = {}
    local presets_csv=io.open(session_name.."/presets.csv")

    _ = presets_csv:read("*line") -- first line are headers
    preset_line = presets_csv:read("*line")
    while preset_line ~= nil
    do
        preset_row = {}
        field_pos=0

        preset_row.slot, field_pos = _csv_get_next_field(preset_line,field_pos)
        preset_row.displayName, field_pos = _csv_get_next_field(preset_line,field_pos)
        preset_row.module1, field_pos = _csv_get_next_field(preset_line,field_pos)
        preset_row.module2, field_pos = _csv_get_next_field(preset_line,field_pos)
        preset_row.module3, field_pos = _csv_get_next_field(preset_line,field_pos)
        preset_row.module4, field_pos = _csv_get_next_field(preset_line,field_pos)
        preset_row.module5, field_pos = _csv_get_next_field(preset_line,field_pos)
        audioHash, field_pos = _csv_get_next_field(preset_line,field_pos)
        preset_row.filenamePrefix = preset_row.displayName:gsub("%s*$","")
        preset_row.filenamePrefix = preset_row.filenamePrefix:gsub(" ","_").."-"..audioHash
        table.insert(preset_rows, preset_row)

        preset_line = presets_csv:read("*line")
    end

    return preset_rows
end

return SessionPresets

