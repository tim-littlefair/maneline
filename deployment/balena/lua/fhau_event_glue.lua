#! lua

function _debug_mark(c)
    io.stdout:write(c)
    io.stdout:flush()
end

pegasus_handler = require 'pegasus.handler'
local pegasus_sock = nil
function get_pegasus_event_client(port)
    socket = require 'socket'
    local pegasus_sock = socket.bind('*', port)
    assert(pegasus_sock)
    function callback(req,res)
        _debug_mark("|gps_cb|")
        res:write("HW!")
        return res:close()
    end
    local hdlr = pegasus_handler:new(callback, ".")
    function pegasus_hdlr_fn()
        first_line = client:read("*line")
        return hdlr:processRequest(port, client)
    end
    retval = {}
    retval._socket = pegasus_sock
    retval._client = nil
    function retval:fd()
        if(self._client)
        then
            -- A connection has already been accepted 
            -- but not yet handled
            return self._client
        end
        self._socket:settimeout(1.0)
        self._client = self._socket:accept()
        return self._client
    end
    function retval:handler()
        assert(self._client~=nil)
        first_line = "XXX" -- self._socket.read("*line")
        self._client:close()
        self._client=nil
        return first_line
    end
    return retval
end

function get_stdin_fd()
    socket = require 'socket'
    fake_stdin = socket.tcp()
    fake_stdin:close()
    fake_stdin:setfd(0)
    fake_stdin:settimeout(0.1)
    return fake_stdin
end

socket = require 'socket'

fd_stdin = get_stdin_fd()
pegasus_evclt = get_pegasus_event_client(9090)
_ACTIVE_TIMEOUT = 0.1
_PASSIVE_TIMEOUT = 1.0
timeout=_ACTIVE_TIMEOUT
while(1)
do
    fd_stdin:settimeout(timeout)
    fd_array = { fd_stdin }
    pegasus_client_fd = pegasus_evclt:fd()
    if(pegasus_client_fd)
    then
        pegasus_client_fd:settimeout(timeout)
        fd_array[#fd_array+1] = pegasus_client_fd
    end
    _debug_mark(#fd_array)
    select_rv = socket.select(fd_array,nil, timeout)
    if(#select_rv==0)
    then
        timeout=_PASSIVE_TIMEOUT
        _debug_mark(".")
    elseif(select_rv[#select_rv]==pegasus_client_fd)
    then
        _debug_mark("<")
        timeout=_ACTIVE_TIMEOUT
        print("pegasus",pegasus_evclt:handler())
        _debug_mark(">")
    elseif(select_rv[#select_rv]:getfd()==0)
    then
        stdin_bytes = io.stdin:read("*line")
        if(stdin_bytes)
        then
            timeout=_ACTIVE_TIMEOUT
            print("stdin:",stdin_bytes)
        else
            timeout=_PASSIVE_TIMEOUT
            _debug_mark("#")
        end
    else
            timeout=_PASSIVE_TIMEOUT
            _debug_mark("!")
    end
end

--[[
  copas.addserver(server_sock, copas.handler(function(client_sock)
    hdlr:processRequest(server_port, client_sock)
  end, opts.sslparams))

  hdlr.log:info('Pegasus is up on %s://%s:%s', opts.sslparams and "https" or "http", server_ip, server_port)
  return server_sock
require 'cqueues'
]]
