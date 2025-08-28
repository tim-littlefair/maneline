#! /usr/bin/lua

-- run.lua
-- The purpose of this package is to define the startup
-- structure of the web/CLI application, in which
-- client objects wrapping the stdin CLI and the pegasus.lua
-- web server are instantiated and handed to the event_loop
-- package for single-threaded multiplexing.

-- Part of the maneline project released under GPL 2.0
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/maneline/blob/main/LICENSE

local lfs = require 'lfs'
local socket = require 'socket'
local pegasus_evtclt = require("pegasus_event_client")
local event_loop = require('event_loop')
local fhau_cli = require('fhau_cli')

-- scripts/run_web_cli.sh is intended to provide
-- a way to simulate the balena deploy environment
-- on the Linux desktop development platform.end
-- The simulated environment set up by this scripts
-- accesses the directory containing lua scripts
-- via a symlink, so the actual base directory of
-- the install (i.e. symlink target) needs to be
-- passed in as a parameter as, on the development
-- environment Balena simulation, ".." points to the
-- parent of the symlink not the base of the simulated
-- Balena install.
if #arg>0
then
    lfs.chdir(arg[1])
else
    lfs.chdir("..")
end

-- The pegasus handler and the FHAU CLI subprocess must 
-- both be created after the current directory is set
pegasus_evtclt:create_handler(lfs.currentdir())
fhau_cli:purge_stale_session_dirs(10)
fhau_cli:start_fhau_cli()

function get_stdin_event_client()
    local retval = {}
    retval._socket = socket.tcp()
    retval._socket:close()
    retval._socket:setfd(0)
    function retval:fd()
        return self._socket
    end
    function retval:settimeout(t)
        return self._socket:settimeout(t)
    end
    function retval:handler(stdin_bytes)
        io.stdout:write(stdin_bytes)
        fhau_cli:relay_stdin_line(stdin_bytes)
    end    
    return retval
end

local stdin_evtclt = get_stdin_event_client()

event_loop.run_event_loop(
    stdin_evtclt,
    pegasus_evtclt,
    true, -- enable debug
    0.2, -- active timeout
    1.0 -- passive timeout
)

