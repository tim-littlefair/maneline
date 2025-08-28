#!/usr/bin/lua

-- pegasus_event_client.lua
-- The purpose of this package is to implement a client
-- object wrapping the execution of the pegasus.lua
-- web server.

-- Part of the maneline project released under GPL 2.0
-- Copyright: Tim Littlefair 2025
-- For copying rules see
-- https://github.com/tim-littlefair/maneline/blob/main/LICENSE

local logging = require('logging')
local socket = require('socket')
local pegasus = require('pegasus')
local pegasus_handler = require('pegasus.handler')
local web_ui = require('web_ui')

local logger = logging.new(
    function(self,level,message)
        print(level, message)
        return true
    end
)
logger:setLevel(logging.WARN)
logger:info("Pegasus logging enabled")

-- Running the service on port 8080
-- means that, for balena deploys alongside
-- balenalabs' "browser" block, the browser
-- should find the service automatcally
local port = 8080

local retval = {}

retval._port = port
retval._socket, err = socket.bind('*', port)
assert(retval._socket, err)

function callback(request,response)
    -- io.stdout:write(retval._phdlr==nil)
    io.stdout:write("Request: ", request:method()," ",request:path())
    web_ui:handle(request,response)
    retval = response:close()
    return retval
end
retval._phdlr=nil
retval._client = nil

function retval:create_handler(rundir)
    assert(self._phdlr==nil)
    self._phdlr = pegasus_handler:new(callback, rundir)
    self._phdlr.log = logger
end

function retval:settimeout(timeout)
    self._socket:settimeout(timeout)
end

function retval:fd()
    if(self._client==nil)
    then
        self._client = self._socket:accept()
    end
    return self._client
end

function retval:handler()
    assert(self._client~=nil)
    assert(self._phdlr~=nil)
    self._phdlr:processRequest(self._port,self._client)
    self._client:close()
    self._client=nil
end

return retval
