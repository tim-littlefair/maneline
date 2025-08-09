#! lua

function _debug_mark(c)
    io.stdout:write(c)
    io.stdout:flush()
end

function get_pegasus_event_client(port)
    local socket = require 'socket'
    pegasus_handler = require 'pegasus.handler'

    local retval = {}
    retval._port = port
    retval._socket, err = socket.bind('*', port)
    assert(retval._socket, err)
    function callback(req,res)
        res:write("HW!")
        return res:close()
    end
    retval._phdlr = pegasus_handler:new(callback, ".")
    retval._client = nil
    function retval:fd()
        if(self._client)
        then
            -- A connection has already been accepted 
            -- but not yet handled
            return self._client
        end
        self._socket:settimeout(0.1)
        self._client = self._socket:accept()
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
    retval._socket:settimeout(0.1)
    function retval:fd()
        return self._socket
    end
    function retval:settimeout(t)
        return self._socket:settimeout(t)
    end
    function retval:handler()
        return nil
    end    
    return retval
end

function run_event_loop(stdin_evtclt, pegasus_evtclt)
    socket = require 'socket'

    _ACTIVE_TIMEOUT = 0.1
    _PASSIVE_TIMEOUT = 1.0
    timeout=_ACTIVE_TIMEOUT
    while(1)
    do
        stdin_evtclt:settimeout(timeout)
        fd_array = { stdin_evtclt:fd() }
        pegasus_client_fd = pegasus_evtclt:fd()
        if(pegasus_client_fd)
        then
            pegasus_client_fd:settimeout(timeout)
            fd_array[#fd_array+1] = pegasus_client_fd
        end

        select_rv = socket.select(fd_array, nil, timeout)
        if(#select_rv==0)
        then
            _debug_mark("!")
            timeout=_PASSIVE_TIMEOUT
        elseif(select_rv[#select_rv]==pegasus_client_fd)
        then
            _debug_mark("p<")
            pegasus_evtclt:handler()
            timeout=_ACTIVE_TIMEOUT
            _debug_mark(">\n")
        else
            stdin_bytes = io.stdin:read("*line")
            if(stdin_bytes)
            then
                _debug_mark("s<")
                print(stdin_bytes)
                _debug_mark(">\n")
                timeout=_ACTIVE_TIMEOUT
            else
                timeout=_PASSIVE_TIMEOUT
            end
        end
    end
end

local _stdin_evtclt = get_stdin_event_client()
local _pegasus_evtclt = get_pegasus_event_client(9090)

run_event_loop(_stdin_evtclt, _pegasus_evtclt)

