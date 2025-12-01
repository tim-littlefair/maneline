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
local retained_sessions = nil

function get_session(session_name)
    session_end_time=lfs.attributes(session_name,"modification")
    session_start_time=session_end_time
    if session_end_time==nil
    then
        return nil
    end
    iter, dir_obj = lfs.dir(session_name)
    f=dir_obj:next()
    while(f~=nil)
    do
        if f ~= ".."
        then
            file_mod_time = lfs.attributes(session_name.."/"..f,"modification")
            if file_mod_time>session_end_time
            then
                session_end_time=file_mod_time
            elseif file_mod_time<session_start_time
            then
                session_start_time = file_mod_time
            end
        end
        f=dir_obj:next()
    end
    session = {}
    session.name = session_name
    session.start_date = os.date("%y-%m-%d",session_start_time)
    session.start_time = os.date("%H:%M",session_start_time)
    session_duration_seconds = session_end_time-session_start_time
    if(session_duration_seconds<=120)
    then
        session.duration=session_duration_seconds.." secs"
    elseif(session_duration_seconds<=120*60)
    then
        session.duration=math.floor(0.5+(session_duration_seconds/60)).." mins"
    else
        session.duration=math.floor(0.5+(session_duration_seconds/(60*60))).." hours"
    end
    return session
end

function Sessions:get_sessions(
    retained_session_names,
    current_session_name
)
    current_session = get_session(current_session_name)
    if current_session == nil
    then
        lfs.mkdir(current_session_name)
        current_session = get_session(current_session_name)
    end
    all_sessions = {}
    table.insert(all_sessions,current_session)
    if retained_sessions == nil
    then
        retained_sessions = {}
        for _, rsn in ipairs(retained_session_names)
        do
            retained_session = get_session(rsn)
            if retained_session ~= nil
            then
                table.insert(retained_sessions,retained_session)
            end
        end
    end
    for _, rs in ipairs(retained_sessions)
    do
        if rs ~= nil
        then
            table.insert(all_sessions,rs)
        end
    end
    return all_sessions
end

return Sessions


