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

local Sessions = {}

function get_session(session_name)
    iter, dir_obj = lfs.dir(session_name)
    session_end_time=lfs.attributes(session_name,"modification")
    session_start_time=session_end_time
    f=dir_obj:next()
    while(f~=nil)
    do
        file_mod_time = lfs.attributes(session_name.."/"..f,"modification")
        if file_mod_time<session_start_time
        then
            session_start_time=file_mod_time
        end
        f=dir_obj:next()
    end
    session = {}
    session.name = session_name
    session.start_date = os.date("%Y%m%d",session_start_time)
    session.start_time = os.date("%H%M",session_start_time)
    session.duration_mins = math.floor(0.5+((session_end_time-session_start_time)/60))
    return session
end

function Sessions:get_sessions(
    retained_session_names,
    current_session_name
)
    sessions = {}
    for _, rsn in ipairs(retained_session_names)
    do
        table.insert(sessions,get_session(rsn))
    end
    table.insert(sessions,get_session(current_session_name))
    return sessions
end

sessions = Sessions:get_sessions({"docs","git-hooks","maneline-lib"},"assets")
print(cjson.encode(sessions))

return Sessions


