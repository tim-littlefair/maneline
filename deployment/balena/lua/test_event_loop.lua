#! lua

function get_pegasus_event_client(port)
    local socket = require 'socket'
    pegasus_handler = require 'pegasus.handler'

    local retval = {}
    retval._port = port
    retval._socket, err = socket.bind('*', port)
    assert(retval._socket, err)
    function callback(req,res)
        res:write("HW")
        return res:close()
    end
    retval._phdlr = pegasus_handler:new(callback, ".")
    retval._client = nil
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
        self._phdlr:processRequest(self._port,self._client)
        self._client:close()
        self._client=nil
    end
    return retval
end

function get_stdin_event_client()
    socket = require 'socket'

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
    end    
    return retval
end

local event_loop = require('event_loop')

local _stdin_evtclt = get_stdin_event_client()
local _pegasus_evtclt = get_pegasus_event_client(9090)

event_loop.run_event_loop(
    _stdin_evtclt, 
    _pegasus_evtclt,
    true, -- enable debug
    5.0, -- active timeout
    10.0 -- passive timeout
)

